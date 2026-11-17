package com.wanderly.ui.group;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.wanderly.data.remote.dto.GroupMemberRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * A friend on the "Who's coming?" list, before the trip is planned. Either a Wanderly user (found by
 * email; their saved preferences are used) or someone entered by hand with what they like.
 * Passed between screens as JSON.
 */
public class Traveller {

    public static final int MAX_PEOPLE = 8;  // the backend's limit, organiser included

    public String userId;           // null: entered by hand
    public String email;
    public String name;
    public String profilePicture;
    public boolean hasPreferences;  // a Wanderly user who completed the wizard
    public List<String> interests = new ArrayList<>();
    public List<String> priorities = new ArrayList<>();
    public List<String> paces = new ArrayList<>();

    public boolean onWanderly() {
        return userId != null;
    }

    /** A Wanderly user without saved preferences needs them entered here, like someone added by hand. */
    public boolean needsAnswers() {
        return !onWanderly() || !hasPreferences;
    }

    public GroupMemberRequest toRequest() {
        GroupMemberRequest r = new GroupMemberRequest();
        r.userId = userId;
        if (!onWanderly()) r.name = name;
        if (needsAnswers()) {
            r.interests = interests;
            r.priorities = priorities;
            r.paces = paces;
        }
        return r;
    }

    public static List<GroupMemberRequest> toRequests(List<Traveller> travellers) {
        List<GroupMemberRequest> list = new ArrayList<>();
        for (Traveller t : travellers) list.add(t.toRequest());
        return list;
    }

    public static String toJson(List<Traveller> travellers) {
        return new Gson().toJson(travellers);
    }

    public static List<Traveller> fromJson(String json) {
        if (json == null) return new ArrayList<>();
        List<Traveller> list = new Gson().fromJson(json, new TypeToken<List<Traveller>>() {}.getType());
        return list == null ? new ArrayList<>() : list;
    }
}
