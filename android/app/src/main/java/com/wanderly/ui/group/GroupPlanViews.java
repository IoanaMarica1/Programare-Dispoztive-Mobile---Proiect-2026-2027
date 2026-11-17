package com.wanderly.ui.group;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.wanderly.R;
import com.wanderly.data.remote.dto.TripResponse;
import com.wanderly.ui.preferences.PreferencesWizard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * D1: the group parts of the plan screen. A card under the photo (avatars and fairness), a
 * "Great for" row on each stop, and a sheet with every traveller and the group actions.
 * Each traveller keeps their colour, by their place in the group (organiser first).
 */
public final class GroupPlanViews {

    private GroupPlanViews() {
    }

    /** Member id -> place in the group. */
    private static Map<String, Integer> positions(TripResponse trip) {
        Map<String, Integer> byId = new HashMap<>();
        for (int i = 0; i < trip.members.size(); i++) byId.put(trip.members.get(i).id, i);
        return byId;
    }

    private static int percent(Double share) {
        return share == null ? 0 : (int) Math.round(Math.max(0, Math.min(1, share)) * 100);
    }

    public static void bindCard(AppCompatActivity activity, TripResponse trip, View card, Runnable onClick) {
        if (!trip.isGroup()) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        List<MemberAvatars.Person> people = new ArrayList<>();
        for (int i = 0; i < trip.members.size(); i++) {
            TripResponse.Member m = trip.members.get(i);
            people.add(new MemberAvatars.Person(m.name, m.profilePicture, i));
        }
        MemberAvatars.stack(card.findViewById(R.id.groupAvatars), people, 32, 4);

        ((TextView) card.findViewById(R.id.groupTitle)).setText(trip.owner
                ? activity.getString(R.string.plan_group_title, trip.members.size())
                : activity.getString(R.string.plan_group_by, trip.organiserName));
        int fairness = percent(trip.fairness);
        LinearProgressIndicator bar = card.findViewById(R.id.groupFairnessBar);
        bar.setProgressCompat(fairness, true);
        bar.setIndicatorColor(activity.getColor(fairness >= 50 ? R.color.teal_500 : R.color.sunset_500));
        ((TextView) card.findViewById(R.id.groupFairness))
                .setText(activity.getString(R.string.plan_group_fairness, fairness));
        card.setOnClickListener(v -> onClick.run());
    }

    /** Whose tastes the stop matches: "Great for everyone", or the avatars of those it suits. */
    public static void bindSuits(TripResponse trip, TripResponse.Stop stop, View stopCard) {
        View row = stopCard.findViewById(R.id.stopSuits);
        if (!trip.isGroup() || stop.suitedTo == null || stop.suitedTo.isEmpty()) {
            row.setVisibility(View.GONE);
            return;
        }
        row.setVisibility(View.VISIBLE);
        TextView label = stopCard.findViewById(R.id.stopSuitsLabel);
        LinearLayout avatars = stopCard.findViewById(R.id.stopSuitsAvatars);
        if (stop.suitedTo.size() >= trip.members.size()) {
            label.setText(R.string.plan_group_everyone);
            label.setTextColor(stopCard.getContext().getColor(R.color.avatar_text));
            label.setBackgroundResource(R.drawable.bg_time_pill);
            int pad = Math.round(8 * stopCard.getResources().getDisplayMetrics().density);
            label.setPadding(pad, pad / 4, pad, pad / 4);
            avatars.removeAllViews();
            return;
        }
        Map<String, Integer> byId = positions(trip);
        List<MemberAvatars.Person> people = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (TripResponse.Member m : trip.members) {
            if (!stop.suitedTo.contains(m.id)) continue;
            Integer i = byId.get(m.id);
            people.add(new MemberAvatars.Person(m.name, m.profilePicture, i == null ? 0 : i));
            names.add(m.name);
        }
        MemberAvatars.stack(avatars, people, 24, 5);
        avatars.setContentDescription(stopCard.getContext().getString(R.string.plan_group_suits)
                + " " + TextUtils.join(", ", names));
    }

    /**
     * Everyone in the group with how many stops suit them. The organiser can ask for a new plan;
     * a member can leave the trip.
     */
    public static void showMembersSheet(AppCompatActivity activity, TripResponse trip,
                                        Runnable onRegenerate, Runnable onLeave) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity, R.style.ThemeOverlay_Wanderly_BottomSheet);
        View root = LayoutInflater.from(activity).inflate(R.layout.sheet_group_members, null, false);
        sheet.setContentView(root);

        ((TextView) root.findViewById(R.id.membersFairness))
                .setText(activity.getString(R.string.plan_group_fairness_hint, percent(trip.fairness)));

        int totalStops = trip.stops == null ? 0 : trip.stops.size();
        String[] interestLabels = activity.getResources().getStringArray(PreferencesWizard.Step.INTERESTS.labels);
        List<String> interestValues = PreferencesWizard.Step.INTERESTS.values;
        LinearLayout list = root.findViewById(R.id.membersList);
        for (int i = 0; i < trip.members.size(); i++) {
            TripResponse.Member m = trip.members.get(i);
            View row = LayoutInflater.from(activity).inflate(R.layout.item_group_member, list, false);
            FrameLayout slot = row.findViewById(R.id.memberAvatar);
            slot.addView(MemberAvatars.create(activity, m.name, m.profilePicture, i, 40));
            ((TextView) row.findViewById(R.id.memberName)).setText(m.name);
            row.findViewById(R.id.memberOrganiser).setVisibility(i == 0 ? View.VISIBLE : View.GONE);

            LinearProgressIndicator bar = row.findViewById(R.id.memberBar);
            bar.setIndicatorColor(MemberAvatars.color(activity, i));
            bar.setTrackColor(ColorStateList.valueOf(MemberAvatars.color(activity, i)).withAlpha(40).getDefaultColor());
            bar.setProgressCompat(totalStops == 0 ? 0 : Math.round(100f * m.matchedStops / totalStops), true);

            // Interests are only shared for people entered by hand; the others' stay private.
            String matched = activity.getString(R.string.plan_group_matched, m.matchedStops, totalStops);
            if (m.interests != null && !m.interests.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (int k = 0; k < interestValues.size(); k++) {
                    if (m.interests.contains(interestValues.get(k))) names.add(interestLabels[k]);
                }
                matched += " · " + TextUtils.join(", ", names);
            }
            ((TextView) row.findViewById(R.id.memberMatched)).setText(matched);
            list.addView(row);
        }

        View regenerate = root.findViewById(R.id.membersRegenerate);
        View leave = root.findViewById(R.id.membersLeave);
        regenerate.setVisibility(trip.owner ? View.VISIBLE : View.GONE);
        leave.setVisibility(trip.owner ? View.GONE : View.VISIBLE);
        regenerate.setOnClickListener(v -> {
            sheet.dismiss();
            onRegenerate.run();
        });
        leave.setOnClickListener(v -> {
            sheet.dismiss();
            onLeave.run();
        });
        sheet.show();
    }
}
