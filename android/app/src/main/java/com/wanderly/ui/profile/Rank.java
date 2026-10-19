package com.wanderly.ui.profile;

import androidx.annotation.StringRes;

import com.wanderly.R;

/** Traveler ranks from the project brief: Tourist (0), Explorer (100), Navigator (300), Pathfinder (700), Legend (1500). */
public enum Rank {
    TOURIST(0, R.string.rank_tourist),
    EXPLORER(100, R.string.rank_explorer),
    NAVIGATOR(300, R.string.rank_navigator),
    PATHFINDER(700, R.string.rank_pathfinder),
    LEGEND(1500, R.string.rank_legend);

    public final int minPoints;
    @StringRes
    public final int label;

    Rank(int minPoints, @StringRes int label) {
        this.minPoints = minPoints;
        this.label = label;
    }

    public static Rank forPoints(int points) {
        Rank result = TOURIST;
        for (Rank rank : values()) {
            if (points >= rank.minPoints) result = rank;
        }
        return result;
    }

    /** The rank after this one, or null for the top rank. */
    public Rank next() {
        int nextIndex = ordinal() + 1;
        return nextIndex < values().length ? values()[nextIndex] : null;
    }

    public static int pointsToNextRank(int points) {
        Rank next = forPoints(points).next();
        return next == null ? 0 : next.minPoints - points;
    }

    /** Progress inside the current rank, 0-100. The top rank is always 100. */
    public static int progressPercent(int points) {
        Rank current = forPoints(points);
        Rank next = current.next();
        if (next == null) return 100;
        return (points - current.minPoints) * 100 / (next.minPoints - current.minPoints);
    }
}
