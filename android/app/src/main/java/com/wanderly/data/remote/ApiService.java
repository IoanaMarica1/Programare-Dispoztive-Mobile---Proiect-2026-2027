package com.wanderly.data.remote;

import com.wanderly.data.remote.dto.AuthResponse;
import com.wanderly.data.remote.dto.CountryHistoryResponse;
import com.wanderly.data.remote.dto.LoginRequest;
import com.wanderly.data.remote.dto.PageResponse;
import com.wanderly.data.remote.dto.PreferencesRequest;
import com.wanderly.data.remote.dto.PreferencesResponse;
import com.wanderly.data.remote.dto.RegisterRequest;
import com.wanderly.data.remote.dto.UserResponse;
import com.wanderly.data.remote.dto.UserUpdateRequest;
import com.wanderly.data.remote.dto.VisitResponse;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import com.wanderly.data.remote.dto.GenerateTripRequest;
import com.wanderly.data.remote.dto.TripResponse;
import com.wanderly.data.remote.dto.CandidateResponse;
import com.wanderly.data.remote.dto.StopRequest;
import java.util.List;

public interface ApiService {

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @GET("api/users/profile")
    Call<UserResponse> getProfile();

    @PUT("api/users/profile")
    Call<UserResponse> updateProfile(@Body UserUpdateRequest request);

    @Multipart
    @POST("api/users/profile/avatar")
    Call<UserResponse> uploadAvatar(@Part MultipartBody.Part file);

    /** 204 No Content (null body) means the wizard was never completed. */
    @GET("api/users/preferences")
    Call<PreferencesResponse> getPreferences();

    @PUT("api/users/preferences")
    Call<PreferencesResponse> savePreferences(@Body PreferencesRequest request);

    @GET("api/users/history/countries")
    Call<PageResponse<CountryHistoryResponse>> getVisitedCountries(@Query("page") int page,
                                                                   @Query("size") int size);

    @GET("api/users/history/countries/{country}")
    Call<PageResponse<VisitResponse>> getVisitsInCountry(@Path("country") String country,
                                                         @Query("page") int page,
                                                         @Query("size") int size);

    @POST("api/trips/generate")
    Call<TripResponse> generateTrip(@Body GenerateTripRequest request);

    @GET("api/trips/{tripId}")
    Call<TripResponse> getTrip(@Path("tripId") String tripId);

    /** Places near the trip (nearest to that day's stops first) to add or swap in. */
    @GET("api/trips/{tripId}/candidates")
    Call<List<CandidateResponse>> getCandidates(@Path("tripId") String tripId, @Query("day") int day);

    // Each edit re-plans the day on the server and returns the whole trip.
    @POST("api/trips/{tripId}/stops")
    Call<TripResponse> addStop(@Path("tripId") String tripId, @Body StopRequest request);

    @PUT("api/trips/{tripId}/stops/{stopId}")
    Call<TripResponse> replaceStop(@Path("tripId") String tripId, @Path("stopId") String stopId,
                                   @Body StopRequest request);

    @DELETE("api/trips/{tripId}/stops/{stopId}")
    Call<TripResponse> removeStop(@Path("tripId") String tripId, @Path("stopId") String stopId);
}
