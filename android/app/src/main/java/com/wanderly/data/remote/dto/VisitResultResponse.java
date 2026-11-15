package com.wanderly.data.remote.dto;

/** What a check-in did: the visit, and the points it earned (none when the place was visited before). */
public class VisitResultResponse {
    public VisitResponse visit;
    public boolean firstVisit;
    public int pointsAwarded;
    public int totalPoints;
}
