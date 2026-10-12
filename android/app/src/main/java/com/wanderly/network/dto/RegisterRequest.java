package com.wanderly.network.dto;

public class RegisterRequest {
    public String email;
    public String password;
    public String name;
    public String address;
    public Integer age;

    public RegisterRequest(String email, String password, String name, String address, Integer age) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.address = address;
        this.age = age;
    }
}