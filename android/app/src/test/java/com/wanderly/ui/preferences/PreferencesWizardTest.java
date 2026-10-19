package com.wanderly.ui.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wanderly.data.remote.dto.PreferencesRequest;
import com.wanderly.data.remote.dto.PreferencesResponse;
import com.wanderly.ui.preferences.PreferencesWizard.Step;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

public class PreferencesWizardTest {

    private final PreferencesWizard wizard = new PreferencesWizard();

    @Test
    public void startsOnTravelModeAndCannotContinueWithoutAnswer() {
        assertEquals(Step.TRAVEL_MODE, wizard.currentStep());
        assertTrue(wizard.isFirstStep());
        assertFalse(wizard.canContinue());
        assertFalse(wizard.next());
        assertEquals(Step.TRAVEL_MODE, wizard.currentStep());
    }

    @Test
    public void requiredStepsCannotBeSkipped() {
        assertFalse(wizard.skip());
        wizard.select("SOLO", true);
        wizard.next();
        assertFalse(wizard.skip());
        assertEquals(Step.INTERESTS, wizard.currentStep());
    }

    @Test
    public void interestsNeedAtLeastOneChoice() {
        goToInterests();
        assertFalse(wizard.canContinue());

        wizard.select("FOOD", true);
        wizard.select("ART", true);
        assertTrue(wizard.canContinue());

        wizard.select("FOOD", false);
        wizard.select("ART", false);
        assertFalse(wizard.canContinue());
    }

    @Test
    public void everyStepAcceptsSeveralAnswers() {
        wizard.select("SOLO", true);
        wizard.select("FAMILY", true);
        assertEquals(new LinkedHashSet<>(Arrays.asList("SOLO", "FAMILY")), wizard.selection());
        wizard.next();

        wizard.select("FOOD", true);
        wizard.select("ART", true);
        wizard.next();

        wizard.select("BUDGET", true);
        wizard.select("CULTURE", true);
        wizard.next();

        wizard.select("RELAXED", true);
        wizard.select("BALANCED", true);
        wizard.select("RELAXED", false);

        PreferencesRequest request = wizard.toRequest();
        assertEquals(Arrays.asList("SOLO", "FAMILY"), request.travelModes);
        assertEquals(Arrays.asList("FOOD", "ART"), request.interests);
        assertEquals(Arrays.asList("BUDGET", "CULTURE"), request.priorities);
        assertEquals(Collections.singletonList("BALANCED"), request.paces);
    }

    @Test
    public void travelModeNeedsAtLeastOneChoice() {
        wizard.select("SOLO", true);
        wizard.select("COUPLE", true);
        wizard.select("SOLO", false);
        assertTrue(wizard.canContinue());

        wizard.select("COUPLE", false);
        assertFalse(wizard.canContinue());
    }

    @Test
    public void optionalStepsCanBeSkippedAndAreSentEmpty() {
        goToInterests();
        wizard.select("HISTORY", true);
        wizard.next();

        assertEquals(Step.PRIORITY, wizard.currentStep());
        assertTrue(wizard.currentStep().optional);
        wizard.select("BUDGET", true);
        wizard.select("SOCIAL", true);
        assertTrue(wizard.skip());       // skipping clears the answers

        assertEquals(Step.PACE, wizard.currentStep());
        assertTrue(wizard.isLastStep());
        assertTrue(wizard.skip());       // last step: stays here, ready to save

        PreferencesRequest request = wizard.toRequest();
        assertEquals(Collections.singletonList("SOLO"), request.travelModes);
        assertEquals(Collections.singletonList("HISTORY"), request.interests);
        assertTrue(request.priorities.isEmpty());
        assertTrue(request.paces.isEmpty());
    }

    @Test
    public void backReturnsToThePreviousStepAndKeepsAnswers() {
        goToInterests();
        assertTrue(wizard.back());
        assertEquals(Step.TRAVEL_MODE, wizard.currentStep());
        assertEquals(Collections.singleton("SOLO"), wizard.selection());
        assertFalse(wizard.back());
    }

    @Test
    public void prefillUsesSavedAnswersAndDropsUnknownValues() {
        PreferencesResponse saved = new PreferencesResponse();
        saved.travelModes = Arrays.asList("COUPLE", "SPACESHIP", "FRIENDS");
        saved.interests = Arrays.asList("NATURE", "SKYDIVING", "FOOD");
        saved.priorities = Arrays.asList("CULTURE", "BUDGET");
        saved.paces = Collections.singletonList("TURBO");

        wizard.prefill(saved);
        PreferencesRequest request = wizard.toRequest();

        assertEquals(Arrays.asList("COUPLE", "FRIENDS"), request.travelModes);
        assertEquals(Arrays.asList("NATURE", "FOOD"), request.interests);
        assertEquals(Arrays.asList("CULTURE", "BUDGET"), request.priorities);
        assertTrue(request.paces.isEmpty());
        assertTrue(wizard.canContinue());
    }

    @Test
    public void prefillToleratesMissingLists() {
        PreferencesResponse saved = new PreferencesResponse();
        saved.travelModes = Collections.singletonList("SOLO");

        wizard.prefill(saved);
        PreferencesRequest request = wizard.toRequest();

        assertEquals(Collections.singletonList("SOLO"), request.travelModes);
        assertTrue(request.interests.isEmpty());
        assertTrue(request.priorities.isEmpty());
        assertTrue(request.paces.isEmpty());
    }

    @Test
    public void prefillWithNothingSavedLeavesTheWizardEmpty() {
        wizard.prefill(null);
        assertFalse(wizard.canContinue());
    }

    @Test
    public void restoreKeepsStepAndAnswers() {
        goToInterests();
        wizard.select("ART", true);
        wizard.select("NATURE", true);
        PreferencesResponse answers = wizard.answers();

        PreferencesWizard restored = new PreferencesWizard();
        restored.restore(1, answers);

        assertEquals(Step.INTERESTS, restored.currentStep());
        assertEquals(new LinkedHashSet<>(Arrays.asList("ART", "NATURE")), restored.selection());
    }

    @Test(expected = IllegalArgumentException.class)
    public void selectRejectsValuesOfAnotherStep() {
        wizard.select("FOOD", true);
    }

    private void goToInterests() {
        wizard.select("SOLO", true);
        assertTrue(wizard.next());
        assertEquals(Step.INTERESTS, wizard.currentStep());
    }
}
