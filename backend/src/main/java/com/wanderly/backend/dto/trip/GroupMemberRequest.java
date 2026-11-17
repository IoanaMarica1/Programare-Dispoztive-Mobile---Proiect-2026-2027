package com.wanderly.backend.dto.trip;

import com.wanderly.backend.model.Interest;
import com.wanderly.backend.model.Pace;
import com.wanderly.backend.model.Priority;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * A friend on a group trip, other than the organiser.
 * <ul>
 *   <li>Has the app: send {@code userId} (from the lookup). Their saved preferences are used; the
 *       fields below are only a fallback when they haven't completed the wizard yet.</li>
 *   <li>Doesn't have the app: send {@code name} and their wizard answers.</li>
 * </ul>
 */
public record GroupMemberRequest(
        UUID userId,
        @Size(max = 100, message = "Name must not contain more than 100 characters.")
        String name,
        Set<@NotNull Interest> interests,
        Set<@NotNull Priority> priorities,
        Set<@NotNull Pace> paces
) {
    @AssertTrue(message = "Add a Wanderly user, or a name and at least one interest.")
    public boolean isIdentified() {
        return userId != null
                || (name != null && !name.isBlank() && interests != null && !interests.isEmpty());
    }
}
