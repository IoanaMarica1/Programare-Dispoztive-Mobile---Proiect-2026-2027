package com.wanderly.backend.service;

import com.wanderly.backend.dto.trip.GenerateGroupTripRequest;
import com.wanderly.backend.dto.trip.GenerateTripRequest;
import com.wanderly.backend.dto.trip.GroupMemberRequest;
import com.wanderly.backend.dto.trip.TravellerLookupResponse;
import com.wanderly.backend.dto.trip.TripResponse;
import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Preferences;
import com.wanderly.backend.model.Trip;
import com.wanderly.backend.model.TripMember;
import com.wanderly.backend.model.User;
import com.wanderly.backend.repository.PreferencesRepository;
import com.wanderly.backend.repository.TripMemberRepository;
import com.wanderly.backend.repository.TripRepository;
import com.wanderly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Group trips: one plan for 2 to 8 people. The organiser is always the first member, with their saved
 * preferences. Friends who use Wanderly bring their saved preferences; the others are entered by hand.
 * Everything is merged by {@link GroupPreferences} and planned by {@link TripService}.
 *
 * Members who have an account see a saved group trip read-only; only the organiser can change it.
 *
 * Generation is not @Transactional: it calls slow external services (see TripService.generate).
 */
@Service
@RequiredArgsConstructor
public class GroupTripService {

    private final TripService tripService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PreferencesRepository preferencesRepository;
    private final TripMemberRepository memberRepository;
    private final TripRepository tripRepository;

    /** Finds a friend by email, to add them to a group trip. */
    public TravellerLookupResponse lookup(String organiserEmail, String email) {
        String wanted = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (wanted.equals(organiserEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You're already in the group.");
        }
        User user = userRepository.findByEmail(wanted)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No Wanderly user with that email. Add their preferences by hand instead."));
        boolean hasPreferences = preferencesRepository.findByUserId(user.getId()).isPresent();
        return new TravellerLookupResponse(user.getPublicId(), user.getName(), user.getProfilePicture(),
                hasPreferences);
    }

    public TripResponse generate(String email, GenerateGroupTripRequest req) {
        User organiser = userService.getByEmail(email);
        Preferences own = preferencesRepository.findByUserId(organiser.getId())
                .orElseThrow(() -> unprocessable("Complete your own preferences before planning a group trip."));

        List<TripMember> members = new ArrayList<>();
        members.add(fromPreferences(organiser, own, 1));
        Set<UUID> seen = new HashSet<>(Set.of(organiser.getPublicId()));
        for (GroupMemberRequest m : req.members()) {
            int position = members.size() + 1;
            if (m.userId() == null) {
                members.add(TripMember.builder()
                        .name(m.name().trim()).position(position)
                        .interests(m.interests()).priorities(m.priorities()).paces(m.paces())
                        .build());
                continue;
            }
            if (!seen.add(m.userId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each person can only be added once.");
            }
            User friend = userRepository.findByPublicId(m.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traveller not found"));
            Optional<Preferences> saved = preferencesRepository.findByUserId(friend.getId());
            if (saved.isPresent()) {
                members.add(fromPreferences(friend, saved.get(), position));
            } else if (m.interests() != null && !m.interests().isEmpty()) {
                members.add(TripMember.builder()
                        .user(friend).name(friend.getName()).position(position)
                        .interests(m.interests()).priorities(m.priorities()).paces(m.paces())
                        .build());
            } else {
                throw unprocessable(friend.getName()
                        + " hasn't filled in their preferences yet. Enter them by hand instead.");
            }
        }
        return plan(organiser, req.trip(), members);
    }

    /**
     * A new plan for the same trip and people. Members with an account bring their current
     * preferences. The old plan is discarded if it was still a draft; a saved one is kept.
     */
    public TripResponse regenerate(String email, UUID tripId, Pace pace) {
        Trip old = tripService.load(email, tripId);
        GenerateTripRequest req = new GenerateTripRequest(old.getDestination(), old.getCountry(),
                old.getLatitude(), old.getLongitude(), old.getStartDate(), old.getEndDate(),
                old.getDayStart(), old.getDayEnd(), pace);

        List<TripMember> previous = memberRepository.findByTripIdOrderByPositionAsc(old.getId());
        TripResponse fresh;
        if (previous.isEmpty()) {
            fresh = tripService.generate(email, req);
        } else {
            List<TripMember> members = new ArrayList<>();
            for (TripMember p : previous) {
                // p.getUser() is an uninitialised proxy here (no transaction); only its id is safe to read.
                User user = p.getUser() == null ? null : userRepository.findById(p.getUser().getId()).orElse(null);
                Optional<Preferences> current = user == null ? Optional.empty()
                        : preferencesRepository.findByUserId(user.getId());
                members.add(current
                        .map(prefs -> fromPreferences(user, prefs, p.getPosition(), p.getName()))
                        .orElseGet(() -> TripMember.builder()
                                .user(user).name(p.getName()).position(p.getPosition())
                                .interests(p.getInterests()).priorities(p.getPriorities()).paces(p.getPaces())
                                .build()));
            }
            fresh = plan(userService.getByEmail(email), req, members);
        }
        if (!old.isSaved()) tripService.deleteDraft(email, tripId);
        return fresh;
    }

    /**
     * A member leaves a saved group trip: it stops showing for them. The plan, its tags and its
     * fairness don't change for anyone else. The organiser can't leave their own trip.
     */
    @Transactional
    public void leave(String email, UUID tripId) {
        if (tripRepository.existsByPublicIdAndUserEmail(tripId, email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You organised this trip, so you can't leave it.");
        }
        memberRepository.findMembership(tripId, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"))
                .hide();
    }

    private TripResponse plan(User organiser, GenerateTripRequest req, List<TripMember> members) {
        List<GroupPreferences.Member> group = members.stream()
                .map(m -> new GroupPreferences.Member(m.getPublicId(), m.getName(), m.getInterests(),
                        m.getPriorities(), m.getPaces()))
                .toList();
        GroupPreferences.Profile profile = GroupPreferences.merge(group);
        return tripService.generate(organiser, req, travellers(profile, group), members);
    }

    /**
     * The group's part of the prompt. Members are labelled P1, P2, ... rather than by name, so the
     * names never leave our server.
     */
    static TripService.Travellers travellers(GroupPreferences.Profile profile, List<GroupPreferences.Member> group) {
        StringBuilder rules = new StringBuilder();
        rules.append("- This is a group trip for ").append(group.size()).append(" people with different tastes:\n");
        for (int i = 0; i < group.size(); i++) {
            rules.append("  P").append(i + 1).append(" likes ").append(names(group.get(i).interests())).append('\n');
        }
        rules.append("""
                - Fairness: every day, include at least one stop matching each person's interests.
                - Prefer places that several people like over places only one person likes.
                """);
        if (!profile.priorities().isEmpty()) {
            rules.append("- Group priorities: ").append(profile.priorities()).append(".\n");
        }
        return new TripService.Travellers(profile.interests(), profile.paces(),
                "Travel mode: a group of " + group.size() + " friends.", rules.toString());
    }

    private static String names(Set<Interest> interests) {
        return interests.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
    }

    private static TripMember fromPreferences(User user, Preferences prefs, int position) {
        return fromPreferences(user, prefs, position, user.getName());
    }

    private static TripMember fromPreferences(User user, Preferences prefs, int position, String name) {
        return TripMember.builder()
                .user(user).name(name).position(position)
                .interests(prefs.getInterests()).priorities(prefs.getPriorities()).paces(prefs.getPaces())
                .build();
    }

    private static ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatusCode.valueOf(422), message);
    }
}
