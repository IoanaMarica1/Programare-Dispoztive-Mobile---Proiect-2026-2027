package com.wanderly.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GeocodingClientTest {

    @Test
    @DisplayName("Coada Nominatim: o cerere al cărei rând vine după deadline renunță imediat, fără să aștepte")
    void waitForSlot_givesUpWhenTurnIsAfterDeadline() throws InterruptedException {
        GeocodingClient client = new GeocodingClient();

        assertThat(client.waitForSlot(Instant.now().plusSeconds(5))).isTrue();   // free slot now

        long started = System.currentTimeMillis();
        assertThat(client.waitForSlot(Instant.now().plusMillis(500))).isFalse(); // next slot is in ~1.1 s
        assertThat(System.currentTimeMillis() - started).isLessThan(200);

        assertThat(client.waitForSlot(Instant.now().plusSeconds(5))).isTrue();   // waits ~1.1 s, then goes
        assertThat(System.currentTimeMillis() - started).isGreaterThanOrEqualTo(1000);
    }
}
