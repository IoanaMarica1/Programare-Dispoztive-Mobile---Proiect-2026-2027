package com.wanderly.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.PageResponse;
import com.wanderly.data.remote.dto.VisitResponse;
import com.wanderly.ui.common.Avatars;
import com.wanderly.ui.common.Formats;
import com.wanderly.ui.common.Session;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** The places a traveler visited in one country, newest first. */
public class CountryVisitsActivity extends AppCompatActivity {

    public static final String EXTRA_COUNTRY = "country";
    private static final int PAGE_SIZE = 20;

    private String country;
    private TextView subtitleText;
    private LinearLayout visitsList;
    private View emptyState;
    private Button showMoreButton;
    private ProgressBar progressBar;

    private int nextPage = 0;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_country_visits);

        country = getIntent().getStringExtra(EXTRA_COUNTRY);
        if (country == null) {
            finish();
            return;
        }

        subtitleText = findViewById(R.id.countrySubtitle);
        visitsList = findViewById(R.id.visitsList);
        emptyState = findViewById(R.id.historyEmpty);
        showMoreButton = findViewById(R.id.showMoreButton);
        progressBar = findViewById(R.id.progressBar);

        ((TextView) findViewById(R.id.countryTitle)).setText(country);
        // The empty state here only explains; starting an adventure happens from the profile.
        findViewById(R.id.startFirstAdventureButton).setVisibility(View.GONE);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        showMoreButton.setOnClickListener(v -> loadVisits());

        loadVisits();
    }

    private void loadVisits() {
        if (loading) return;
        loading = true;
        progressBar.setVisibility(View.VISIBLE);
        showMoreButton.setVisibility(View.GONE);

        ApiClient.getService(this)
                .getVisitsInCountry(country, nextPage, PAGE_SIZE)
                .enqueue(new Callback<PageResponse<VisitResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<PageResponse<VisitResponse>> call,
                                           @NonNull Response<PageResponse<VisitResponse>> response) {
                        if (isDestroyed()) return;
                        loading = false;
                        progressBar.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null) {
                            showVisits(response.body());
                        } else if (response.code() == 401) {
                            Session.expire(CountryVisitsActivity.this);
                        } else {
                            Toast.makeText(CountryVisitsActivity.this,
                                    R.string.history_load_failed, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PageResponse<VisitResponse>> call,
                                          @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        loading = false;
                        progressBar.setVisibility(View.GONE);
                        showMoreButton.setVisibility(View.VISIBLE);
                        Toast.makeText(CountryVisitsActivity.this,
                                R.string.error_network, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showVisits(PageResponse<VisitResponse> page) {
        if (page.content != null) {
            for (VisitResponse visit : page.content) {
                visitsList.addView(visitRow(visit));
            }
        }
        nextPage = page.page + 1;

        int total = (int) page.totalElements;
        subtitleText.setText(getResources().getQuantityString(R.plurals.places_visited, total, total));
        emptyState.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        showMoreButton.setVisibility(page.hasNext() ? View.VISIBLE : View.GONE);
    }

    private View visitRow(VisitResponse visit) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_history_row, visitsList, false);
        ((TextView) row.findViewById(R.id.rowIcon)).setText(Avatars.initialOf(visit.objectiveName));
        ((TextView) row.findViewById(R.id.rowTitle)).setText(visit.objectiveName);
        ((TextView) row.findViewById(R.id.rowSubtitle)).setText(
                getString(R.string.visit_meta, visit.city, Formats.date(visit.visitedAt)));
        // A visit has no detail screen yet.
        row.findViewById(R.id.rowChevron).setVisibility(View.GONE);
        row.setClickable(false);
        row.setBackground(null);
        return row;
    }
}
