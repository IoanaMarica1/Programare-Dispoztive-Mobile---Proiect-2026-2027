package com.wanderly.ui.preferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.ApiService;
import com.wanderly.data.remote.dto.GenerateTripRequest;
import com.wanderly.data.remote.dto.TripResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Keeps the trip generation call alive across screen rotations: the activity is recreated, this isn't,
 * so the plan (which takes 20-40 s) is not lost and not requested twice.
 */
public class GenerateTripViewModel extends ViewModel {

    /** What came back; {@link #consume()} makes sure a rotation doesn't open the plan screen twice. */
    public static final class Outcome {
        public final TripResponse trip;
        public final int code;          // HTTP status, 0 for a network error
        public final String message;
        public final GenerateTripRequest request;
        private boolean consumed;

        Outcome(TripResponse trip, int code, String message, GenerateTripRequest request) {
            this.trip = trip;
            this.code = code;
            this.message = message;
            this.request = request;
        }

        /** True the first time only. */
        public boolean consume() {
            if (consumed) return false;
            consumed = true;
            return true;
        }
    }

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Outcome> outcome = new MutableLiveData<>();
    private Call<TripResponse> call;

    public LiveData<Boolean> loading() {
        return loading;
    }

    public LiveData<Outcome> outcome() {
        return outcome;
    }

    public void generate(ApiService api, GenerateTripRequest request) {
        if (Boolean.TRUE.equals(loading.getValue())) return;  // one plan at a time
        loading.setValue(true);
        call = api.generateTrip(request);
        call.enqueue(new Callback<TripResponse>() {
            @Override
            public void onResponse(@NonNull Call<TripResponse> c, @NonNull Response<TripResponse> response) {
                loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    outcome.setValue(new Outcome(response.body(), response.code(), null, request));
                } else {
                    outcome.setValue(new Outcome(null, response.code(), ApiClient.errorMessage(response,
                            "Could not generate plan (" + response.code() + ")"), request));
                }
            }

            @Override
            public void onFailure(@NonNull Call<TripResponse> c, @NonNull Throwable t) {
                loading.setValue(false);
                if (c.isCanceled()) return;
                outcome.setValue(new Outcome(null, 0, "Network error", request));
            }
        });
    }

    @Override
    protected void onCleared() {
        if (call != null) call.cancel();  // the user left the screen for good
    }
}
