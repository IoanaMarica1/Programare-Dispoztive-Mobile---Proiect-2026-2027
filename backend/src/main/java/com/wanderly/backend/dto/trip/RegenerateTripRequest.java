package com.wanderly.backend.dto.trip;

import com.wanderly.backend.model.Pace;

/** Optional body of POST /api/trips/{id}/regenerate. Without a pace, the travellers' preferences decide it. */
public record RegenerateTripRequest(Pace pace) {
}
