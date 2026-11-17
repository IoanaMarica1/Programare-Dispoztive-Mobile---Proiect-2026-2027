package com.wanderly.ui.group;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.wanderly.data.remote.dto.GroupMemberRequest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TravellerTest {

    @Test
    public void wanderlyUserWithPreferencesSendsOnlyTheirId() {
        Traveller t = new Traveller();
        t.userId = "u-1";
        t.name = "Ana";
        t.hasPreferences = true;
        t.interests = Collections.singletonList("FOOD");

        GroupMemberRequest r = t.toRequest();

        assertEquals("u-1", r.userId);
        assertNull(r.name);        // the backend uses their account name
        assertNull(r.interests);   // and their saved preferences
    }

    @Test
    public void wanderlyUserWithoutPreferencesSendsTheAnswersEnteredHere() {
        Traveller t = new Traveller();
        t.userId = "u-2";
        t.name = "Dan";
        t.interests = Arrays.asList("ART", "NATURE");

        GroupMemberRequest r = t.toRequest();

        assertEquals("u-2", r.userId);
        assertNull(r.name);
        assertEquals(Arrays.asList("ART", "NATURE"), r.interests);
    }

    @Test
    public void someoneAddedByHandSendsNameAndAnswers() {
        Traveller t = new Traveller();
        t.name = "Mara";
        t.interests = Collections.singletonList("HISTORY");
        t.paces = Collections.singletonList("RELAXED");

        GroupMemberRequest r = t.toRequest();

        assertNull(r.userId);
        assertEquals("Mara", r.name);
        assertEquals(Collections.singletonList("HISTORY"), r.interests);
        assertEquals(Collections.singletonList("RELAXED"), r.paces);
    }

    @Test
    public void listSurvivesTheTripBetweenScreens() {
        Traveller t = new Traveller();
        t.name = "Mara";
        t.interests = Collections.singletonList("FOOD");

        List<Traveller> back = Traveller.fromJson(Traveller.toJson(Collections.singletonList(t)));

        assertEquals(1, back.size());
        assertEquals("Mara", back.get(0).name);
        assertEquals(Collections.singletonList("FOOD"), back.get(0).interests);
    }

    @Test
    public void missingJsonIsAnEmptyGroup() {
        assertEquals(0, Traveller.fromJson(null).size());
    }
}
