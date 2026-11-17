package com.wanderly.data.remote.dto;

/**
 * One row in the list of saved plans; the stops load when the plan is opened.
 * {@code owner} is false for a group trip someone else organised: it opens read-only.
 */
public class TripSummaryResponse {
    public String id;
    public String destination;
    public String country;
    public String startDate;
    public String endDate;
    public boolean owner = true;
    public String organiserName;
}
