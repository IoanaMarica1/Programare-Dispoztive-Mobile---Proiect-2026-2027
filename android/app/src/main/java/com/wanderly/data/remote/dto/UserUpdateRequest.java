package com.wanderly.data.remote.dto;

public class UserUpdateRequest {
    public String name;
    public String bio;
    public String address;

    public UserUpdateRequest(String name, String bio, String address) {
        this.name = name;
        this.bio = bio;
        this.address = address;
    }
}
