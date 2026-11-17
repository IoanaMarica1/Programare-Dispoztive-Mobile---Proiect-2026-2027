package com.wanderly.data.remote.dto;

import java.util.List;

/**
 * A friend on a group trip. A Wanderly user is sent by {@code userId} (their saved preferences are
 * used, the lists are only a fallback when they have none); anyone else by name and interests.
 */
public class GroupMemberRequest {
    public String userId;
    public String name;
    public List<String> interests;
    public List<String> priorities;
    public List<String> paces;
}
