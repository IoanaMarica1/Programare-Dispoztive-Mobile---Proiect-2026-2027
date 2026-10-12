package com.wanderly.network;

import com.wanderly.network.dto.AuthResponse;
import com.wanderly.network.dto.LoginRequest;
import com.wanderly.network.dto.RegisterRequest;
import com.wanderly.network.dto.UserResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ApiService {

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @GET("api/users/profile")
    Call<UserResponse> getProfile();
}