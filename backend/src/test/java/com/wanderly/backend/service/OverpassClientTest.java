package com.wanderly.backend.service;

import com.wanderly.backend.model.Mood;
import com.wanderly.backend.service.OverpassClient.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverpassClientTest {

    @Test
    @DisplayName("Interogarea cere locuri cu nume, în rază, doar tipurile dispoziției, cu coordonate cu punct zecimal")
    void aroundQuery() {
        String q = OverpassClient.buildAroundQuery(46.77, 23.59, 2100, SurpriseRanker.filters(Mood.RELAXED));

        assertThat(q).startsWith("[out:json][timeout:4];(")
                .contains("nwr(around:2100,46.77000,23.59000)[\"name\"][\"leisure\"~\"(^|;)(")
                .contains("garden").contains("park")
                .contains("[\"amenity\"~\"(^|;)(cafe|library)(;|$)\"]")
                .endsWith(");out center tags 600;");
    }

    @Test
    @DisplayName("Coordonatele negative sau lângă 0 nu folosesc notația științifică (Overpass le-ar respinge)")
    void queryCoordinatesAreFixedPoint() {
        String q = OverpassClient.buildAroundQuery(0.0004, -0.0005, 2100, Map.of("tourism", Set.of("museum")));

        assertThat(q).contains("around:2100,0.00040,-0.00050").doesNotContain("E-");
    }

    @Test
    @DisplayName("Căutarea după referință folosește tipul și numărul elementului")
    void lookupQuery() {
        assertThat(OverpassClient.buildLookupQuery("node/123")).isEqualTo("[out:json][timeout:4];node(123);out center tags;");
        assertThat(OverpassClient.buildLookupQuery("relation/9")).contains("relation(9)");
    }

    @Test
    @DisplayName("Răspunsul: nod cu lat/lon, cale cu center, nume în engleză preferat, fără nume sau poziție = ignorat")
    void parseResponse() {
        Map<String, Object> response = Map.of("elements", List.of(
                Map.of("type", "node", "id", 1, "lat", 46.77, "lon", 23.59,
                        "tags", Map.of("name", "Parcul Central", "name:en", "Central Park", "leisure", "park")),
                Map.of("type", "way", "id", 22L, "center", Map.of("lat", 46.78, "lon", 23.6),
                        "tags", Map.of("name", "Muzeul", "tourism", "museum")),
                Map.of("type", "node", "id", 3, "lat", 46.0, "lon", 23.0, "tags", Map.of("leisure", "park")),
                Map.of("type", "node", "id", 4, "lat", 46.0, "lon", 23.0, "tags", Map.of("name", "  ")),
                Map.of("type", "way", "id", 5, "tags", Map.of("name", "No position")),
                Map.of("type", "node", "id", 6, "lat", 46.0, "lon", 23.0)));

        List<Element> parsed = OverpassClient.parse(response);

        assertThat(parsed).extracting(Element::ref).containsExactly("node/1", "way/22");
        assertThat(parsed.get(0).name()).isEqualTo("Central Park");
        assertThat(parsed.get(0).lat()).isEqualTo(46.77);
        assertThat(parsed.get(1).lon()).isEqualTo(23.6);
        assertThat(parsed.get(1).tags()).containsEntry("tourism", "museum");
    }

    @Test
    @DisplayName("Răspuns fără elemente sau cu forma greșită = listă goală, nu excepție")
    void parseEmpty() {
        assertThat(OverpassClient.parse(Map.of())).isEmpty();
        assertThat(OverpassClient.parse(Map.of("elements", List.of()))).isEmpty();
        assertThat(OverpassClient.parse(Map.of("elements", "oops"))).isEmpty();
        assertThat(OverpassClient.parse(Map.of("elements", List.of("x", 3)))).isEmpty();
    }

    @Test
    @DisplayName("Un nume foarte lung e tăiat la 200 de caractere (coloana din baza de date)")
    void longNamesAreCut() {
        Map<String, Object> response = Map.of("elements", List.of(
                Map.of("type", "node", "id", 1, "lat", 1.0, "lon", 1.0, "tags", Map.of("name", "x".repeat(500)))));

        assertThat(OverpassClient.parse(response).get(0).name()).hasSize(200);
    }

    /** Counts the requests that would have gone out, and answers with whatever the test says. */
    private static class FakeOverpass extends OverpassClient {
        int requests = 0;
        java.util.function.Supplier<Map<String, Object>> answer;

        @Override
        Map<String, Object> send(String endpoint, org.springframework.util.MultiValueMap<String, String> form,
                                 java.time.Duration timeout) {
            requests++;
            return answer.get();
        }
    }

    private static final Map<String, Set<String>> ANY = Map.of("leisure", Set.of("park"));

    @Test
    @DisplayName("Un server care nu răspunde → excepție; imediat după, căutările nu mai încearcă Overpass (pauză 30 s)")
    void coolDownAfterFailure() {
        FakeOverpass overpass = new FakeOverpass();
        overpass.answer = () -> { throw new IllegalStateException("502"); };
        Instant deadline = Instant.now().plusSeconds(10);

        assertThatThrownBy(() -> overpass.around(46.77, 23.59, 2100, ANY, deadline)).isInstanceOf(IllegalStateException.class);
        assertThat(overpass.requests).isEqualTo(2);   // both servers were tried

        assertThatThrownBy(() -> overpass.around(46.77, 23.59, 2100, ANY, deadline)).isInstanceOf(IllegalStateException.class);
        assertThat(overpass.requests).isEqualTo(2);   // nothing was sent the second time
    }

    @Test
    @DisplayName("Răspuns bun → elementele parsate; al doilea server e încercat doar după un eșec rapid")
    void secondServerAfterFastFailure() {
        FakeOverpass overpass = new FakeOverpass();
        int[] calls = {0};
        overpass.answer = () -> {
            if (calls[0]++ == 0) throw new IllegalStateException("502");
            return Map.of("elements", List.of(Map.of("type", "node", "id", 1, "lat", 1.0, "lon", 2.0,
                    "tags", Map.of("name", "Park"))));
        };

        List<Element> found = overpass.around(46.77, 23.59, 2100, ANY, Instant.now().plusSeconds(10));

        assertThat(found).extracting(Element::name).containsExactly("Park");
        assertThat(overpass.requests).isEqualTo(2);
    }

    @Test
    @DisplayName("Răspuns 200 cu „remark: runtime error” (date incomplete) e tratat ca eșec")
    void incompleteAnswerIsAFailure() {
        FakeOverpass overpass = new FakeOverpass();
        overpass.answer = () -> Map.of("remark", "runtime error: Query timed out", "elements", List.of());

        assertThatThrownBy(() -> overpass.around(46.77, 23.59, 2100, ANY, Instant.now().plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Dacă bugetul de timp s-a terminat deja, nu se trimite nimic și nu începe pauza")
    void noTimeLeftDoesNotTripTheBreaker() {
        FakeOverpass overpass = new FakeOverpass();
        overpass.answer = () -> Map.of("elements", List.of());

        assertThatThrownBy(() -> overpass.around(46.77, 23.59, 2100, ANY, Instant.now().minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(overpass.requests).isZero();

        assertThat(overpass.around(46.77, 23.59, 2100, ANY, Instant.now().plusSeconds(10))).isEmpty();
        assertThat(overpass.requests).isEqualTo(1);
    }

    @Test
    @DisplayName("O căutare după referință (check-in) care eșuează nu pune în pauză căutările pe zonă")
    void failedLookupDoesNotTripTheCoolDown() {
        FakeOverpass overpass = new FakeOverpass();
        overpass.answer = () -> { throw new IllegalStateException("502"); };
        assertThatThrownBy(() -> overpass.lookup("way/7", Instant.now().plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class);

        overpass.answer = () -> Map.of("elements", List.of());
        assertThat(overpass.around(46.77, 23.59, 2100, ANY, Instant.now().plusSeconds(10))).isEmpty();
    }

    @Test
    @DisplayName("Căutarea după referință (check-in) merge și cât timp căutările sunt în pauză")
    void lookupIgnoresTheCoolDown() {
        FakeOverpass overpass = new FakeOverpass();
        overpass.answer = () -> { throw new IllegalStateException("502"); };
        assertThatThrownBy(() -> overpass.around(46.77, 23.59, 2100, ANY, Instant.now().plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class);

        overpass.answer = () -> Map.of("elements", List.of(Map.of("type", "way", "id", 7, "center", Map.of("lat", 1.0, "lon", 2.0),
                "tags", Map.of("name", "Castle"))));

        assertThat(overpass.lookup("way/7", Instant.now().plusSeconds(10))).hasValueSatisfying(
                e -> assertThat(e.ref()).isEqualTo("way/7"));
    }
}
