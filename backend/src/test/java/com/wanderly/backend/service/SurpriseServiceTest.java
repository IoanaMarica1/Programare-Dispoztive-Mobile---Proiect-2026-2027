package com.wanderly.backend.service;

import com.wanderly.backend.dto.surprise.CheckInRequest;
import com.wanderly.backend.dto.surprise.SurprisePlaceResponse;
import com.wanderly.backend.dto.surprise.SurpriseRequest;
import com.wanderly.backend.dto.visit.VisitResponse;
import com.wanderly.backend.dto.visit.VisitResultResponse;
import com.wanderly.backend.model.Mood;
import com.wanderly.backend.service.GeocodingClient.Address;
import com.wanderly.backend.service.GeocodingClient.Coordinates;
import com.wanderly.backend.service.OverpassClient.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurpriseServiceTest {

    private static final double LAT = 46.7700;
    private static final double LON = 23.5900;

    @Mock
    private OverpassClient overpass;
    @Mock
    private GeocodingClient geocoder;
    @Mock
    private VisitService visitService;

    private SurpriseService service;

    @BeforeEach
    void setUp() {
        service = new SurpriseService(overpass, geocoder, visitService);
    }

    /** A "park" that is always acceptable, whatever the time of day the test runs at: 24/7 hours. */
    private static Element park(long id, String name, double metersNorth) {
        return new Element("node/" + id, name, LAT + metersNorth / 111_000.0, LON,
                Map.of("leisure", "park", "opening_hours", "24/7"));
    }

    private static SurpriseRequest request(Mood mood, List<String> exclude) {
        return new SurpriseRequest(mood, LAT, LON, "Europe/Bucharest", exclude);
    }

    private void overpassReturns(Element... places) {
        when(overpass.around(anyDouble(), anyDouble(), anyInt(), any(), any())).thenReturn(List.of(places));
    }

    @Test
    @DisplayName("Returnează locurile potrivite, cel mai bun primul, cu distanță, timp de mers și explicație")
    void suggest_returnsTheBestPlaceFirst() {
        overpassReturns(park(1, "Far Park", 1500), park(2, "Near Park", 200));

        List<SurprisePlaceResponse> all = service.suggest(request(Mood.RELAXED, null));

        assertThat(all).extracting(SurprisePlaceResponse::id).containsExactly("node/2", "node/1");
        SurprisePlaceResponse res = all.get(0);
        assertThat(res.id()).isEqualTo("node/2");
        assertThat(res.name()).isEqualTo("Near Park");
        assertThat(res.category()).isEqualTo("Park");
        assertThat(res.distanceMeters()).isBetween(195, 210);
        assertThat(res.walkingMinutes()).isPositive();
        assertThat(res.why()).startsWith("A calm park");
        assertThat(res.openNote()).isNotBlank();
    }

    @Test
    @DisplayName("Nu se trimit mai mult de 5 locuri, chiar dacă zona are multe")
    void suggest_capsTheNumberOfPlaces() {
        overpassReturns(park(1, "Park A", 100), park(2, "Park B", 200), park(3, "Park C", 300),
                park(4, "Park D", 400), park(5, "Park E", 500), park(6, "Park F", 600), park(7, "Park G", 700));

        assertThat(service.suggest(request(Mood.RELAXED, null))).hasSize(SurpriseService.MAX_OPTIONS);
    }

    @Test
    @DisplayName("Locurile excluse nu mai apar în listă")
    void suggest_skipsExcludedPlaces() {
        overpassReturns(park(1, "First", 100), park(2, "Second", 400), park(3, "Third", 800));

        assertThat(service.suggest(request(Mood.RELAXED, List.of("node/1"))))
                .extracting(SurprisePlaceResponse::id).containsExactly("node/2", "node/3");
        assertThat(service.suggest(request(Mood.RELAXED, List.of("node/1", "node/2"))))
                .extracting(SurprisePlaceResponse::id).containsExactly("node/3");
    }

    @Test
    @DisplayName("Cererile repetate din aceeași zonă și cu aceeași dispoziție folosesc cache-ul: o singură interogare")
    void suggest_cachesTheLookup() {
        overpassReturns(park(1, "First", 100), park(2, "Second", 400));

        service.suggest(request(Mood.RELAXED, null));
        service.suggest(request(Mood.RELAXED, List.of("node/1")));
        service.suggest(new SurpriseRequest(Mood.RELAXED, LAT + 0.0001, LON + 0.0001, "Europe/Bucharest", null));

        verify(overpass, times(1)).around(anyDouble(), anyDouble(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Altă dispoziție sau altă zonă înseamnă o nouă interogare")
    void suggest_cacheIsPerMoodAndArea() {
        overpassReturns(park(1, "First", 100));

        service.suggest(request(Mood.RELAXED, null));
        assertThatThrownBy(() -> service.suggest(request(Mood.HUNGRY, null))).isInstanceOf(ResponseStatusException.class);
        service.suggest(new SurpriseRequest(Mood.RELAXED, LAT + 0.01, LON, "Europe/Bucharest", null));

        verify(overpass, times(3)).around(anyDouble(), anyDouble(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Locurile se caută în jurul centrului celulei, cu raza de 2 km plus marja")
    void suggest_searchesAroundTheCellCentre() {
        overpassReturns(park(1, "First", 100));

        service.suggest(new SurpriseRequest(Mood.RELAXED, 46.77049, 23.59051, "Europe/Bucharest", null));

        verify(overpass).around(eq(46.770), eq(23.591), eq(2100), any(), any());
    }

    @Test
    @DisplayName("Nimic deschis care se potrivește → 404 cu mesaj; după ce toate au fost arătate → alt 404")
    void suggest_nothingFound() {
        overpassReturns();

        assertThatThrownBy(() -> service.suggest(request(Mood.HUNGRY, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getReason()).contains("2 km").contains("another mood");
                });

        overpassReturns(park(1, "Only Park", 100));
        assertThatThrownBy(() -> service.suggest(request(Mood.RELAXED, List.of("node/1"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getReason()).contains("everything open nearby");
                });
    }

    @Test
    @DisplayName("Overpass și Nominatim indisponibile → 503, iar eșecul nu intră în cache (următoarea atingere reîncearcă)")
    void suggest_everythingDownIsNotCached() {
        when(overpass.around(anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("Overpass unavailable"))
                .thenReturn(List.of(park(1, "First", 100)));
        when(geocoder.nearby(anyString(), anyDouble(), anyDouble(), anyInt(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.suggest(request(Mood.RELAXED, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        verify(geocoder, times(3)).nearby(anyString(), anyDouble(), anyDouble(), anyInt(), any());

        assertThat(service.suggest(request(Mood.RELAXED, null))).extracting(SurprisePlaceResponse::id)
                .containsExactly("node/1");
    }

    @Test
    @DisplayName("Overpass ocupat → planul B: căutări Nominatim pentru cele mai potrivite tipuri, rezultatele se combină fără dubluri")
    void suggest_fallsBackToNominatim() {
        when(overpass.around(anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("Overpass unavailable"));
        Element city = park(1, "City Park", 300);
        when(geocoder.nearby(eq("park"), anyDouble(), anyDouble(), eq(2100), any())).thenReturn(List.of(city));
        when(geocoder.nearby(eq("garden"), anyDouble(), anyDouble(), eq(2100), any()))
                .thenReturn(List.of(city, new Element("node/2", "Rose Garden", LAT, LON + 0.001,
                        Map.of("leisure", "garden", "opening_hours", "24/7"))));
        when(geocoder.nearby(eq("beach"), anyDouble(), anyDouble(), anyInt(), any())).thenReturn(null);  // one search failed

        List<SurprisePlaceResponse> first = service.suggest(request(Mood.RELAXED, null));
        List<SurprisePlaceResponse> second = service.suggest(request(Mood.RELAXED, List.of("node/1")));

        assertThat(first).extracting(SurprisePlaceResponse::id).containsExactlyInAnyOrder("node/1", "node/2");
        assertThat(second).extracting(SurprisePlaceResponse::id).containsExactly("node/2");
        // The second tap reuses the fallback result instead of searching again.
        verify(overpass, times(1)).around(anyDouble(), anyDouble(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Fus orar necunoscut → 400; lipsa fusului orar folosește ora serverului")
    void suggest_timeZone() {
        overpassReturns(park(1, "First", 100));

        assertThatThrownBy(() -> service.suggest(new SurpriseRequest(Mood.RELAXED, LAT, LON, "Mars/Olympus", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));

        assertThat(service.suggest(new SurpriseRequest(Mood.RELAXED, LAT, LON, null, null))).hasSize(1);
        assertThat(service.suggest(new SurpriseRequest(Mood.RELAXED, LAT, LON, "  ", null))).hasSize(1);
    }

    // ------------------------------------------------------------ check-in

    private static final Element CASTLE = new Element("way/77", "Bánffy Castle", 46.7706, 23.5906, Map.of());

    @Test
    @DisplayName("Check-in la ~10 m: serverul își caută singur locul și înregistrează vizita cu orașul și țara de pe hartă")
    void checkIn_nearby_recordsTheVisit() {
        when(overpass.lookup(eq("way/77"), any())).thenReturn(Optional.of(CASTLE));
        when(geocoder.reverse(eq(46.7706), eq(23.5906), any())).thenReturn(Optional.of(new Address("Cluj-Napoca", "Romania")));
        VisitResultResponse expected = new VisitResultResponse(
                new VisitResponse(null, "Bánffy Castle", "Cluj-Napoca", "Romania", 46.7706, 23.5906, Instant.now()),
                true, 20, 50);
        when(visitService.recordSurpriseVisit(eq("ana@test.com"), eq("Bánffy Castle"), eq("Cluj-Napoca"),
                eq("Romania"), any(Coordinates.class))).thenReturn(expected);

        VisitResultResponse res = service.checkIn("ana@test.com", new CheckInRequest("way/77", 46.77065, 23.59065));

        assertThat(res).isSameAs(expected);
        verify(visitService).recordSurpriseVisit("ana@test.com", "Bánffy Castle", "Cluj-Napoca", "Romania",
                new Coordinates(46.7706, 23.5906));
    }

    @Test
    @DisplayName("Check-in de la ~1 km → 422 înainte de orice căutare de oraș și fără puncte")
    void checkIn_tooFar() {
        when(overpass.lookup(eq("way/77"), any())).thenReturn(Optional.of(CASTLE));

        assertThatThrownBy(() -> service.checkIn("ana@test.com", new CheckInRequest("way/77", 46.7796, 23.5906)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(422);
                    assertThat(e.getReason()).contains("Bánffy Castle").contains("150 m");
                });
        verify(geocoder, never()).reverse(anyDouble(), anyDouble(), any());
        verify(visitService, never()).recordSurpriseVisit(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Loc dispărut de pe hartă → 404; hartă indisponibilă → 503 (la căutare și la oraș)")
    void checkIn_unavailableOrMissing() {
        when(overpass.lookup(eq("node/1"), any())).thenReturn(Optional.empty());
        when(overpass.lookup(eq("node/2"), any())).thenThrow(new IllegalStateException("down"));
        when(overpass.lookup(eq("way/77"), any())).thenReturn(Optional.of(CASTLE));
        when(geocoder.reverse(anyDouble(), anyDouble(), any())).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service.checkIn("ana@test.com", new CheckInRequest("node/1", LAT, LON)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.checkIn("ana@test.com", new CheckInRequest("node/2", LAT, LON)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThatThrownBy(() -> service.checkIn("ana@test.com", new CheckInRequest("way/77", 46.7706, 23.5906)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    @DisplayName("Loc fără oraș sau țară cunoscute pe hartă → 422, fără vizită")
    void checkIn_noTownKnown() {
        when(overpass.lookup(eq("way/77"), any())).thenReturn(Optional.of(CASTLE));
        when(geocoder.reverse(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn("ana@test.com", new CheckInRequest("way/77", 46.7706, 23.5906)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
        verify(visitService, never()).recordSurpriseVisit(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Orașul și țara foarte lungi sunt tăiate la 100 de caractere (coloanele din baza de date)")
    void checkIn_longAddressIsCut() {
        when(overpass.lookup(eq("way/77"), any())).thenReturn(Optional.of(CASTLE));
        when(geocoder.reverse(anyDouble(), anyDouble(), any()))
                .thenReturn(Optional.of(new Address("c".repeat(150), "k".repeat(150))));

        service.checkIn("ana@test.com", new CheckInRequest("way/77", 46.7706, 23.5906));

        verify(visitService).recordSurpriseVisit(eq("ana@test.com"), eq("Bánffy Castle"),
                eq("c".repeat(100)), eq("k".repeat(100)), any());
    }

    @Test
    @DisplayName("Mulțimea de excluderi poate fi și goală sau lipsă")
    void exclude_nullOrEmpty() {
        overpassReturns(park(1, "First", 100));

        assertThat(service.suggest(new SurpriseRequest(Mood.RELAXED, LAT, LON, "UTC", List.of()))).hasSize(1);
        assertThat(service.suggest(new SurpriseRequest(Mood.RELAXED, LAT, LON, "UTC", null))).hasSize(1);
    }
}
