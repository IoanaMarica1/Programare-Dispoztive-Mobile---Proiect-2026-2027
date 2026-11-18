package com.wanderly.ui.surprise;

import androidx.annotation.StringRes;

import com.wanderly.R;

/** The six moods of Surprise Me. {@link #name()} is what the backend expects (its Mood enum). */
public enum Mood {
    CURIOUS("\uD83E\uDD14", R.string.mood_curious),
    RELAXED("\uD83D\uDE0C", R.string.mood_relaxed),
    SOCIAL("\uD83E\uDD42", R.string.mood_social),
    ADVENTUROUS("\uD83E\uDDD7", R.string.mood_adventurous),
    HUNGRY("\uD83C\uDF7D\uFE0F", R.string.mood_hungry),
    CULTURAL("\uD83C\uDFAD", R.string.mood_cultural);

    /** Shown big on the mood's tile; the same on every phone, so it is not a translatable string. */
    public final String emoji;

    @StringRes
    public final int label;

    Mood(String emoji, @StringRes int label) {
        this.emoji = emoji;
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
