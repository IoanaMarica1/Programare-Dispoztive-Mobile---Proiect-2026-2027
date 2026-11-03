package com.wanderly.data.remote;

import android.content.Context;

import com.google.gson.Gson;
import com.wanderly.BuildConfig;
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
    /** Set per build in app/build.gradle.kts (Gradle property "apiUrl"). */
    public static final String BASE_URL = BuildConfig.BASE_URL;

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
                    .readTimeout(60, TimeUnit.SECONDS)
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

    /** The backend returns uploads as relative paths, e.g. "/uploads/avatars/x.jpg". */
    public static String absoluteUrl(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return BASE_URL + (path.startsWith("/") ? path.substring(1) : path);
    }

    /**
     * The backend's own explanation of a failed call: its field errors (e.g. "Trips can last at most
     * 14 days."), else its message, else {@code fallback}.
     */
    public static String errorMessage(Response<?> response, String fallback) {
        ErrorResponse error = parseError(response);
        if (error != null && error.errors != null && !error.errors.isEmpty()) {
            return android.text.TextUtils.join("\n", error.errors.values());
        }
        if (error != null && error.message != null && !error.message.isEmpty()) {
            return error.message;
        }
        return fallback;
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