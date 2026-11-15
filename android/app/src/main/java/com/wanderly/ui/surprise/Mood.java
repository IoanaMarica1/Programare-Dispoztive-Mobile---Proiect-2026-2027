package com.wanderly.ui.surprise;

import androidx.annotation.StringRes;

import com.wanderly.R;

/** The six moods of Surprise Me. {@link #name()} is what the backend expects (its Mood enum). */
public enum Mood {
    CURIOUS(R.string.mood_curious),
    RELAXED(R.string.mood_relaxed),
    SOCIAL(R.string.mood_social),
    ADVENTUROUS(R.string.mood_adventurous),
    HUNGRY(R.string.mood_hungry),
    CULTURAL(R.string.mood_cultural);

    @StringRes
    public final int label;

    Mood(@StringRes int label) {
        this.label = label;
    }

    /** The mood a saved name stands for, or null (no saved state, or a name from another version). */
    public static Mood fromName(String name) {
        if (name == null) return null;
        for (Mood mood : values()) {
            if (mood.name().equals(name)) return mood;
        }
        return null;
    }
}
