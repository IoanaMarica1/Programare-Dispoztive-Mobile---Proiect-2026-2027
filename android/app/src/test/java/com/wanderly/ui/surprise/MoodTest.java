package com.wanderly.ui.surprise;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MoodTest {

    /** The names the backend's Mood enum accepts: a different spelling here would be a 400 there. */
    @Test
    public void namesMatchTheBackendEnumInTheOrderTheyAreShown() {
        String[] names = new String[Mood.values().length];
        for (int i = 0; i < names.length; i++) names[i] = Mood.values()[i].name();

        assertArrayEquals(new String[]{"CURIOUS", "RELAXED", "SOCIAL", "ADVENTUROUS", "HUNGRY", "CULTURAL"}, names);
    }

    @Test
    public void everyMoodHasALabel() {
        for (Mood mood : Mood.values()) {
            assertEquals(mood.name(), true, mood.label != 0);
        }
    }

    @Test
    public void savedNamesComeBackAsTheSameMood() {
        for (Mood mood : Mood.values()) {
            assertEquals(mood, Mood.fromName(mood.name()));
        }
    }

    @Test
    public void unknownOrMissingNamesGiveNull() {
        assertNull(Mood.fromName(null));
        assertNull(Mood.fromName(""));
        assertNull(Mood.fromName("relaxed"));
        assertNull(Mood.fromName("SLEEPY"));
    }
}
