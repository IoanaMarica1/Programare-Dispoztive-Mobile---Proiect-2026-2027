package com.wanderly.ui.group;

import android.animation.ObjectAnimator;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wanderly.R;
import com.wanderly.data.remote.ApiClient;
import com.wanderly.data.remote.dto.TravellerLookupResponse;
import com.wanderly.ui.common.Dialogs;
import com.wanderly.ui.common.Session;
import com.wanderly.ui.preferences.PreferencesWizard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The bottom sheet that adds one traveller, or edits someone whose tastes were entered here.
 * Problems with the lookup (unknown email, own email, already added, offline) open a dialog;
 * missing fields are marked in place.
 */
final class AddTravellerSheet {

    private final AppCompatActivity activity;
    private final List<Traveller> others;   // everyone else already in the group
    private final Traveller editing;        // null when adding
    private final Consumer<Traveller> onDone;

    private final BottomSheetDialog dialog;
    private final View root;
    private final MaterialButtonToggleGroup modeToggle;
    private final View wanderlyPanel, answersPanel, morePanel, foundCard;
    private final TextInputLayout emailLayout, nameLayout;
    private final TextInputEditText emailInput, nameInput;
    private final LinearProgressIndicator lookupProgress;
    private final ChipGroup interestsGroup, prioritiesGroup, pacesGroup;
    private final TextView interestsError;

    private TravellerLookupResponse found;
    private String foundEmail;
    private Call<TravellerLookupResponse> lookup;

    static void show(AppCompatActivity activity, List<Traveller> others, @Nullable Traveller editing,
                     Consumer<Traveller> onDone) {
        new AddTravellerSheet(activity, others, editing, onDone).dialog.show();
    }

    private AddTravellerSheet(AppCompatActivity activity, List<Traveller> others, @Nullable Traveller editing,
                              Consumer<Traveller> onDone) {
        this.activity = activity;
        this.others = others;
        this.editing = editing;
        this.onDone = onDone;

        dialog = new BottomSheetDialog(activity, R.style.ThemeOverlay_Wanderly_BottomSheet);
        root = LayoutInflater.from(activity).inflate(R.layout.sheet_add_traveller, null, false);
        dialog.setContentView(root);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.setOnDismissListener(d -> {
            if (lookup != null) lookup.cancel();
        });

        modeToggle = root.findViewById(R.id.modeToggle);
        wanderlyPanel = root.findViewById(R.id.wanderlyPanel);
        answersPanel = root.findViewById(R.id.answersPanel);
        morePanel = root.findViewById(R.id.morePanel);
        foundCard = root.findViewById(R.id.foundCard);
        emailLayout = root.findViewById(R.id.emailLayout);
        nameLayout = root.findViewById(R.id.nameLayout);
        emailInput = root.findViewById(R.id.emailInput);
        nameInput = root.findViewById(R.id.nameInput);
        lookupProgress = root.findViewById(R.id.lookupProgress);
        interestsGroup = root.findViewById(R.id.interestsGroup);
        prioritiesGroup = root.findViewById(R.id.prioritiesGroup);
        pacesGroup = root.findViewById(R.id.pacesGroup);
        interestsError = root.findViewById(R.id.interestsError);

        addChoices(interestsGroup, PreferencesWizard.Step.INTERESTS);
        addChoices(prioritiesGroup, PreferencesWizard.Step.PRIORITY);
        addChoices(pacesGroup, PreferencesWizard.Step.PACE);
        interestsGroup.setOnCheckedStateChangeListener((g, ids) -> {
            if (!ids.isEmpty()) interestsError.setVisibility(View.GONE);
        });

        root.findViewById(R.id.moreToggle).setOnClickListener(v -> setMoreOpen(morePanel.getVisibility() != View.VISIBLE));
        modeToggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) showMode(id == R.id.modeWanderly);
        });

        emailLayout.setEndIconOnClickListener(v -> find(false));
        emailInput.setOnEditorActionListener((v, action, e) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            find(false);
            return true;
        });
        emailInput.addTextChangedListener(new SimpleWatcher(() -> emailLayout.setError(null)));
        nameInput.addTextChangedListener(new SimpleWatcher(() -> nameLayout.setError(null)));
        root.findViewById(R.id.foundChange).setOnClickListener(v -> clearFound());
        root.findViewById(R.id.sheetSubmit).setOnClickListener(v -> submit());

        if (editing == null) {
            showMode(true);
        } else {
            fill(editing);
        }
    }

    // ------------------------------------------------------------------ modes

    private void showMode(boolean wanderly) {
        wanderlyPanel.setVisibility(wanderly ? View.VISIBLE : View.GONE);
        nameLayout.setVisibility(wanderly ? View.GONE : View.VISIBLE);
        updateAnswersVisibility();
        (wanderly ? emailInput : nameInput).requestFocus();
    }

    private boolean wanderlyMode() {
        return modeToggle.getCheckedButtonId() == R.id.modeWanderly;
    }

    /** Tastes are asked for people added by hand and for Wanderly users who never saved theirs. */
    private void updateAnswersVisibility() {
        boolean needed = !wanderlyMode() || (found != null && !found.hasPreferences);
        answersPanel.setVisibility(needed ? View.VISIBLE : View.GONE);
    }

    private void setMoreOpen(boolean open) {
        morePanel.setVisibility(open ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.moreChevron).animate().rotation(open ? 180f : 0f).setDuration(200).start();
    }

    /** Editing someone already on the list: no switching between the two kinds of traveller. */
    private void fill(Traveller t) {
        ((TextView) root.findViewById(R.id.sheetTitle))
                .setText(activity.getString(R.string.edit_traveller_title, t.name));
        ((MaterialButton) root.findViewById(R.id.sheetSubmit)).setText(R.string.add_traveller_save);
        modeToggle.setVisibility(View.GONE);
        modeToggle.check(t.onWanderly() ? R.id.modeWanderly : R.id.modeManual);
        if (t.onWanderly()) {
            TravellerLookupResponse r = new TravellerLookupResponse();
            r.userId = t.userId;
            r.name = t.name;
            r.profilePicture = t.profilePicture;
            r.hasPreferences = t.hasPreferences;
            showFound(r, t.email);
            root.findViewById(R.id.foundChange).setVisibility(View.GONE);
        } else {
            nameInput.setText(t.name);
        }
        check(interestsGroup, t.interests);
        check(prioritiesGroup, t.priorities);
        check(pacesGroup, t.paces);
        if (!t.priorities.isEmpty() || !t.paces.isEmpty()) setMoreOpen(true);
        showMode(t.onWanderly());
    }

    // ------------------------------------------------------------------ lookup

    /**
     * Looks the email up. With {@code addWhenFound} (the main button was tapped before the search),
     * someone whose saved preferences can be used joins straight away.
     */
    private void find(boolean addWhenFound) {
        String email = text(emailInput).toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            emailLayout.setError(activity.getString(R.string.error_email_required));
            shake(emailLayout);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(activity.getString(R.string.error_email_invalid));
            shake(emailLayout);
            return;
        }
        for (Traveller t : others) {
            if (email.equalsIgnoreCase(t.email)) {
                showDuplicate(t.name);
                return;
            }
        }
        if (lookup != null) lookup.cancel();
        setLookingUp(true);
        lookup = ApiClient.getService(activity.getApplicationContext()).lookupTraveller(email);
        lookup.enqueue(new Callback<TravellerLookupResponse>() {
            @Override
            public void onResponse(@NonNull Call<TravellerLookupResponse> call,
                                   @NonNull Response<TravellerLookupResponse> response) {
                if (!dialog.isShowing()) return;
                setLookingUp(false);
                TravellerLookupResponse body = response.body();
                if (response.isSuccessful() && body != null) {
                    for (Traveller t : others) {
                        if (body.userId != null && body.userId.equals(t.userId)) {
                            showDuplicate(t.name);
                            return;
                        }
                    }
                    showFound(body, email);
                    if (addWhenFound && body.hasPreferences) submit();
                } else if (response.code() == 404) {
                    showNotFound(email);
                } else if (response.code() == 400) {
                    Dialogs.info(activity, R.drawable.ic_person, activity.getString(R.string.dialog_self_title),
                                    activity.getString(R.string.dialog_self_message))
                            .setPositiveButton(R.string.action_ok, null)
                            .show();
                } else if (response.code() == 401) {
                    dialog.dismiss();
                    Session.expire(activity);
                } else {
                    Dialogs.showError(activity, activity.getString(R.string.dialog_generic_title),
                            ApiClient.errorMessage(response, activity.getString(R.string.dialog_plan_failed_busy)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<TravellerLookupResponse> call, @NonNull Throwable t) {
                if (call.isCanceled() || !dialog.isShowing()) return;
                setLookingUp(false);
                Dialogs.showNetworkError(activity, () -> find(addWhenFound));
            }
        });
    }

    private void setLookingUp(boolean on) {
        lookupProgress.setVisibility(on ? View.VISIBLE : View.GONE);
        emailLayout.setEndIconActivated(!on);
        emailLayout.setEnabled(!on);
    }

    private void showFound(TravellerLookupResponse r, String email) {
        found = r;
        foundEmail = email;
        FrameLayout slot = root.findViewById(R.id.foundAvatar);
        slot.removeAllViews();
        slot.addView(MemberAvatars.create(activity, r.name, r.profilePicture, others.size() + 1, 44));
        ((TextView) root.findViewById(R.id.foundName)).setText(r.name);
        ((TextView) root.findViewById(R.id.foundStatus)).setText(r.hasPreferences
                ? R.string.add_traveller_found_ready : R.string.add_traveller_found_no_prefs);
        emailLayout.setVisibility(View.GONE);
        foundCard.setVisibility(View.VISIBLE);
        updateAnswersVisibility();
    }

    private void clearFound() {
        found = null;
        foundEmail = null;
        foundCard.setVisibility(View.GONE);
        emailLayout.setVisibility(View.VISIBLE);
        emailInput.requestFocus();
        updateAnswersVisibility();
    }

    private void showNotFound(String email) {
        Dialogs.info(activity, R.drawable.ic_search, activity.getString(R.string.dialog_not_found_title),
                        activity.getString(R.string.dialog_not_found_message, email))
                .setNegativeButton(R.string.dialog_not_found_other, (d, w) -> {
                    emailInput.selectAll();
                    emailInput.requestFocus();
                })
                .setPositiveButton(R.string.dialog_not_found_action, (d, w) -> modeToggle.check(R.id.modeManual))
                .show();
    }

    private void showDuplicate(String name) {
        Dialogs.info(activity, R.drawable.ic_group, activity.getString(R.string.dialog_duplicate_title),
                        activity.getString(R.string.dialog_duplicate_message, name))
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    // ------------------------------------------------------------------ saving

    private void submit() {
        Traveller t = new Traveller();
        if (wanderlyMode()) {
            if (found == null) {
                find(true);  // no need to tap the magnifier first
                return;
            }
            t.userId = found.userId;
            t.email = foundEmail;
            t.name = found.name;
            t.profilePicture = found.profilePicture;
            t.hasPreferences = found.hasPreferences;
        } else {
            t.name = text(nameInput);
            if (t.name.isEmpty()) {
                nameLayout.setError(activity.getString(R.string.error_name_required));
                shake(nameLayout);
                return;
            }
            for (Traveller o : others) {
                if (!o.onWanderly() && t.name.equalsIgnoreCase(o.name)) {
                    showDuplicate(o.name);
                    return;
                }
            }
        }
        if (t.needsAnswers()) {
            t.interests = checked(interestsGroup);
            t.priorities = checked(prioritiesGroup);
            t.paces = checked(pacesGroup);
            if (t.interests.isEmpty()) {
                interestsError.setVisibility(View.VISIBLE);
                shake(interestsGroup);
                return;
            }
        }
        dialog.dismiss();
        onDone.accept(t);
    }

    // ------------------------------------------------------------------ helpers

    private void addChoices(ChipGroup group, PreferencesWizard.Step step) {
        String[] labels = activity.getResources().getStringArray(step.labels);
        for (int i = 0; i < step.values.size(); i++) {
            Chip chip = (Chip) LayoutInflater.from(activity).inflate(R.layout.item_choice_chip_small, group, false);
            chip.setText(labels[i]);
            chip.setTag(step.values.get(i));
            chip.setId(View.generateViewId());
            group.addView(chip);
        }
    }

    private static void check(ChipGroup group, List<String> values) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            chip.setChecked(values != null && values.contains((String) chip.getTag()));
        }
    }

    private static List<String> checked(ChipGroup group) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            if (chip.isChecked()) values.add((String) chip.getTag());
        }
        return values;
    }

    private static String text(TextView input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    /** A small sideways shake that points at what needs fixing. */
    static void shake(View view) {
        float d = view.getResources().getDisplayMetrics().density;
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0, 10 * d, -10 * d, 6 * d, -6 * d, 0)
                .setDuration(350)
                .start();
    }

    private static final class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable onChange;

        SimpleWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(android.text.Editable s) { onChange.run(); }
    }
}
