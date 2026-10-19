package com.wanderly.ui.preferences;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.ErrorResponse;
import com.wanderly.data.remote.dto.PreferencesResponse;
import com.wanderly.ui.common.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Four-step wizard: travel mode, interests, then the optional priority and pace. */
public class PreferencesActivity extends AppCompatActivity {

    private static final String STATE_STEP = "step";
    private static final String STATE_TRAVEL_MODES = "travelModes";
    private static final String STATE_INTERESTS = "interests";
    private static final String STATE_PRIORITIES = "priorities";
    private static final String STATE_PACES = "paces";

    private final PreferencesWizard wizard = new PreferencesWizard();

    private TextView stepText, optionalTag, stepTitle, stepSubtitle, backLink, skipLink;
    private LinearProgressIndicator wizardProgress;
    private ChipGroup choicesGroup;
    private Button nextButton;
    private ProgressBar progressBar;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        stepText = findViewById(R.id.stepText);
        optionalTag = findViewById(R.id.optionalTag);
        stepTitle = findViewById(R.id.stepTitle);
        stepSubtitle = findViewById(R.id.stepSubtitle);
        backLink = findViewById(R.id.backLink);
        skipLink = findViewById(R.id.skipLink);
        wizardProgress = findViewById(R.id.wizardProgress);
        choicesGroup = findViewById(R.id.choicesGroup);
        nextButton = findViewById(R.id.nextButton);
        progressBar = findViewById(R.id.progressBar);

        nextButton.setOnClickListener(v -> onNext());
        skipLink.setOnClickListener(v -> onSkip());
        backLink.setOnClickListener(v -> onBack());

        // System back walks through the steps before leaving the wizard.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });

        if (savedInstanceState != null) {
            wizard.restore(savedInstanceState.getInt(STATE_STEP), answersFrom(savedInstanceState));
            render();
        } else {
            render();
            loadSavedPreferences();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        PreferencesResponse answers = wizard.answers();
        outState.putInt(STATE_STEP, wizard.stepIndex());
        outState.putStringArrayList(STATE_TRAVEL_MODES, new ArrayList<>(answers.travelModes));
        outState.putStringArrayList(STATE_INTERESTS, new ArrayList<>(answers.interests));
        outState.putStringArrayList(STATE_PRIORITIES, new ArrayList<>(answers.priorities));
        outState.putStringArrayList(STATE_PACES, new ArrayList<>(answers.paces));
    }

    private PreferencesResponse answersFrom(Bundle state) {
        PreferencesResponse answers = new PreferencesResponse();
        answers.travelModes = state.getStringArrayList(STATE_TRAVEL_MODES);
        answers.interests = state.getStringArrayList(STATE_INTERESTS);
        answers.priorities = state.getStringArrayList(STATE_PRIORITIES);
        answers.paces = state.getStringArrayList(STATE_PACES);
        return answers;
    }

    /** Pre-fills the wizard with what the traveler chose last time (204 = nothing saved yet). */
    private void loadSavedPreferences() {
        setBusy(true);
        ApiClient.getService(this)
                .getPreferences()
                .enqueue(new Callback<PreferencesResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PreferencesResponse> call,
                                           @NonNull Response<PreferencesResponse> response) {
                        if (isDestroyed()) return;
                        setBusy(false);
                        if (response.isSuccessful()) {
                            wizard.prefill(response.body());
                            render();
                        } else if (response.code() == 401) {
                            Session.expire(PreferencesActivity.this);
                        } else {
                            Toast.makeText(PreferencesActivity.this,
                                    R.string.wizard_load_failed, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PreferencesResponse> call, @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        setBusy(false);
                        Toast.makeText(PreferencesActivity.this,
                                R.string.wizard_load_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void render() {
        PreferencesWizard.Step step = wizard.currentStep();
        int number = wizard.stepIndex() + 1;

        stepText.setText(getString(R.string.wizard_step, number, wizard.stepCount()));
        wizardProgress.setProgressCompat(number * 100 / wizard.stepCount(), true);
        optionalTag.setVisibility(step.optional ? View.VISIBLE : View.GONE);
        stepTitle.setText(step.title);
        stepSubtitle.setText(step.subtitle);

        renderChoices(step);

        backLink.setText(wizard.isFirstStep() ? R.string.action_cancel : R.string.action_back);
        skipLink.setVisibility(step.optional ? View.VISIBLE : View.INVISIBLE);
        nextButton.setText(wizard.isLastStep() ? R.string.action_save_preferences : R.string.action_next);
        updateNextButton();
    }

    private void renderChoices(PreferencesWizard.Step step) {
        choicesGroup.setOnCheckedStateChangeListener(null);
        choicesGroup.removeAllViews();
        choicesGroup.setSingleSelection(false);
        choicesGroup.setSelectionRequired(false);

        String[] labels = getResources().getStringArray(step.labels);
        List<String> values = step.values;
        Set<String> selected = wizard.selection();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            Chip chip = (Chip) inflater.inflate(R.layout.item_choice_chip, choicesGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(labels[i]);
            chip.setChecked(selected.contains(value));
            chip.setOnCheckedChangeListener((button, checked) -> {
                wizard.select(value, checked);
                updateNextButton();
            });
            choicesGroup.addView(chip);
        }
    }

    private void updateNextButton() {
        nextButton.setEnabled(!busy && wizard.canContinue());
    }

    private void onNext() {
        boolean wasLast = wizard.isLastStep();
        if (!wizard.next()) return;
        if (wasLast) save(); else render();
    }

    private void onSkip() {
        boolean wasLast = wizard.isLastStep();
        if (!wizard.skip()) return;
        if (wasLast) save(); else render();
    }

    private void onBack() {
        if (busy) return;
        if (wizard.back()) render(); else finish();
    }

    private void save() {
        setBusy(true);
        ApiClient.getService(this)
                .savePreferences(wizard.toRequest())
                .enqueue(new Callback<PreferencesResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PreferencesResponse> call,
                                           @NonNull Response<PreferencesResponse> response) {
                        if (isDestroyed()) return;
                        setBusy(false);
                        if (response.isSuccessful()) {
                            Toast.makeText(PreferencesActivity.this,
                                    R.string.wizard_saved, Toast.LENGTH_SHORT).show();
                            finish();
                        } else if (response.code() == 401) {
                            Session.expire(PreferencesActivity.this);
                        } else {
                            ErrorResponse error = ApiClient.parseError(response);
                            Toast.makeText(PreferencesActivity.this,
                                    error != null && error.message != null
                                            ? error.message : getString(R.string.wizard_save_failed),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<PreferencesResponse> call, @NonNull Throwable t) {
                        if (isDestroyed()) return;
                        setBusy(false);
                        Toast.makeText(PreferencesActivity.this,
                                R.string.error_network, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        skipLink.setEnabled(!busy);
        backLink.setEnabled(!busy);
        updateNextButton();
    }
}
