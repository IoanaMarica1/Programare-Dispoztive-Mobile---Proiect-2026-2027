package com.wanderly.backend.dto.surprise;

/**
 * The one place Surprise Me suggests. {@code id} is the OpenStreetMap reference ("node/123"); the app sends it
 * back to skip this place ("Another one") and to check in. Nullable fields are simply left out of the card.
 */
public record SurprisePlaceResponse(
        String id,
        String name,
        String category,
        double latitude,
        double longitude,
        int distanceMeters,
        int walkingMinutes,
        String why,
        String openNote,
        String photoUrl,
        String address,
        String website,
        boolean moreAvailable
) {
    /** "node/123", "way/45", "relation/6". */
    public static final String ID_PATTERN = "^(node|way|relation)/[0-9]{1,12}$";
}
