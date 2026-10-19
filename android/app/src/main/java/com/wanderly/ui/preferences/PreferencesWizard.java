package com.wanderly.ui.preferences;

import androidx.annotation.ArrayRes;
import androidx.annotation.StringRes;

import com.wanderly.R;
import com.wanderly.data.remote.dto.PreferencesRequest;
import com.wanderly.data.remote.dto.PreferencesResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State machine behind the preference wizard: which step is shown, what has been
 * picked so far and whether the traveler may move on. It has no Android views,
 * so the validation rules can be unit-tested.
 */
public class PreferencesWizard {

    /** Every step accepts several answers; required steps need at least one. */
    public enum Step {
        TRAVEL_MODE(false, R.string.wizard_travel_mode_title, R.string.wizard_travel_mode_subtitle,
                R.array.travel_mode_labels, "SOLO", "COUPLE", "FRIENDS", "FAMILY"),
        INTERESTS(false, R.string.wizard_interests_title, R.string.wizard_interests_subtitle,
                R.array.interest_labels, "HISTORY", "NATURE", "FOOD", "NIGHTLIFE", "ART", "ADVENTURE"),
        PRIORITY(true, R.string.wizard_priority_title, R.string.wizard_priority_subtitle,
                R.array.priority_labels, "SOCIAL", "RELAXATION", "CULTURE", "BUDGET"),
        PACE(true, R.string.wizard_pace_title, R.string.wizard_pace_subtitle,
                R.array.pace_labels, "RELAXED", "BALANCED", "PACKED");

        public final boolean optional;
        @StringRes public final int title;
        @StringRes public final int subtitle;
        /** Labels shown on the chips, in the same order as {@link #values}. */
        @ArrayRes public final int labels;
        /** Enum names expected by the backend. */
        public final List<String> values;

        Step(boolean optional, @StringRes int title, @StringRes int subtitle,
             @ArrayRes int labels, String... values) {
            this.optional = optional;
            this.title = title;
            this.subtitle = subtitle;
            this.labels = labels;
            this.values = Collections.unmodifiableList(Arrays.asList(values));
        }
    }

    private static final Step[] STEPS = Step.values();

    private int index = 0;
    private final Map<Step, Set<String>> answers = new EnumMap<>(Step.class);

    public PreferencesWizard() {
        for (Step step : STEPS) {
            answers.put(step, new LinkedHashSet<>());
        }
    }

    public Step currentStep() {
        return STEPS[index];
    }

    public int stepIndex() {
        return index;
    }

    public int stepCount() {
        return STEPS.length;
    }

    public boolean isFirstStep() {
        return index == 0;
    }

    public boolean isLastStep() {
        return index == STEPS.length - 1;
    }

    /** Required steps need an answer; optional ones can always be left. */
    public boolean canContinue() {
        Step step = currentStep();
        return step.optional || !answers.get(step).isEmpty();
    }

    /** Moves forward when allowed. On the last step it only reports whether the answers can be saved. */
    public boolean next() {
        if (!canContinue()) return false;
        if (!isLastStep()) index++;
        return true;
    }

    public boolean back() {
        if (isFirstStep()) return false;
        index--;
        return true;
    }

    /** Clears the answers of an optional step and moves on. Required steps cannot be skipped. */
    public boolean skip() {
        Step step = currentStep();
        if (!step.optional) return false;
        answers.get(step).clear();
        if (!isLastStep()) index++;
        return true;
    }

    public void select(String value, boolean checked) {
        Step step = currentStep();
        if (!step.values.contains(value)) {
            throw new IllegalArgumentException(value + " is not an option of " + step);
        }
        if (checked) answers.get(step).add(value); else answers.get(step).remove(value);
    }

    /** What is currently picked on the shown step, used to check the chips. */
    public Set<String> selection() {
        return Collections.unmodifiableSet(answers.get(currentStep()));
    }

    /** Pre-fills the answers from the saved preferences, ignoring values this app does not know. */
    public void prefill(PreferencesResponse saved) {
        if (saved == null) return;
        keepKnown(Step.TRAVEL_MODE, saved.travelModes);
        keepKnown(Step.INTERESTS, saved.interests);
        keepKnown(Step.PRIORITY, saved.priorities);
        keepKnown(Step.PACE, saved.paces);
    }

    /** Restores the shown step after the screen is recreated, e.g. on rotation. */
    public void restore(int stepIndex, PreferencesResponse answers) {
        prefill(answers);
        index = Math.max(0, Math.min(stepIndex, STEPS.length - 1));
    }

    public PreferencesResponse answers() {
        PreferencesResponse answers = new PreferencesResponse();
        answers.travelModes = listOf(Step.TRAVEL_MODE);
        answers.interests = listOf(Step.INTERESTS);
        answers.priorities = listOf(Step.PRIORITY);
        answers.paces = listOf(Step.PACE);
        return answers;
    }

    public PreferencesRequest toRequest() {
        return new PreferencesRequest(listOf(Step.TRAVEL_MODE), listOf(Step.INTERESTS),
                listOf(Step.PRIORITY), listOf(Step.PACE));
    }

    private void keepKnown(Step step, List<String> values) {
        Set<String> picked = answers.get(step);
        picked.clear();
        if (values == null) return;
        for (String value : values) {
            if (step.values.contains(value)) picked.add(value);
        }
    }

    private List<String> listOf(Step step) {
        return new ArrayList<>(answers.get(step));
    }
}
