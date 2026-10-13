package com.wanderly.data.remote;

import android.content.Context;

import com.google.gson.Gson;
import com.wanderly.data.local.TokenManager;
import com.wanderly.data.remote.dto.ErrorResponse;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    public static final String BASE_URL = "http://localhost:8080/";

    private static ApiService service;

    private ApiClient() {
    }

    public static synchronized ApiService getService(Context context) {
        if (service == null) {
            TokenManager tokenManager = new TokenManager(context);

            // only for development, showcase the requests in logcat
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        String token = tokenManager.getToken();
                        if (token == null) {
                            return chain.proceed(original);
                        }
                        Request withToken = original.newBuilder()
                                .header("Authorization", "Bearer " + token)
                                .build();
                        return chain.proceed(withToken);
                    })
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            service = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return service;
    }
    public static ErrorResponse parseError(Response<?> response) {
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                return new Gson().fromJson(body.charStream(), ErrorResponse.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}