package com.wanderly.ui.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RankTest {

    @Test
    public void forPoints_usesTheHighestThresholdReached() {
        assertEquals(Rank.TOURIST, Rank.forPoints(0));
        assertEquals(Rank.TOURIST, Rank.forPoints(99));
        assertEquals(Rank.EXPLORER, Rank.forPoints(100));
        assertEquals(Rank.NAVIGATOR, Rank.forPoints(300));
        assertEquals(Rank.PATHFINDER, Rank.forPoints(1499));
        assertEquals(Rank.LEGEND, Rank.forPoints(1500));
        assertEquals(Rank.LEGEND, Rank.forPoints(99_999));
    }

    @Test
    public void forPoints_negativePointsStayTourist() {
        assertEquals(Rank.TOURIST, Rank.forPoints(-10));
    }

    @Test
    public void next_isNullOnlyForTheTopRank() {
        assertEquals(Rank.EXPLORER, Rank.TOURIST.next());
        assertEquals(Rank.LEGEND, Rank.PATHFINDER.next());
        assertNull(Rank.LEGEND.next());
    }

    @Test
    public void pointsToNextRank_countsDownToTheNextThreshold() {
        assertEquals(100, Rank.pointsToNextRank(0));
        assertEquals(60, Rank.pointsToNextRank(40));
        assertEquals(200, Rank.pointsToNextRank(100));
        assertEquals(0, Rank.pointsToNextRank(1500));
    }

    @Test
    public void progressPercent_isMeasuredInsideTheCurrentRank() {
        assertEquals(0, Rank.progressPercent(0));
        assertEquals(40, Rank.progressPercent(40));
        assertEquals(0, Rank.progressPercent(100));
        assertEquals(50, Rank.progressPercent(200));   // halfway from 100 to 300
        assertEquals(100, Rank.progressPercent(1500));
    }
}
