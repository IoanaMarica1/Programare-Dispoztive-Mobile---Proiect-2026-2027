package com.wanderly.data.remote;

import com.wanderly.data.remote.dto.AuthResponse;
import com.wanderly.data.remote.dto.LoginRequest;
import com.wanderly.data.remote.dto.RegisterRequest;
import com.wanderly.data.remote.dto.UserResponse;

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