package com.wanderly.backend.service;

import com.wanderly.backend.model.Mood;
import com.wanderly.backend.service.OverpassClient.Element;
import com.wanderly.backend.service.SurpriseRanker.Candidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SurpriseRankerTest {

    // Piața Unirii, Cluj-Napoca. A degree of latitude is ~111 km, so 0.001 is ~111 m north.
    private static final double LAT = 46.7700;
    private static final double LON = 23.5900;
    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");

    // 2026-11-16 is a Monday.
    private static final ZonedDateTime MONDAY_NOON = at(16, 12, 0);
    private static final ZonedDateTime MONDAY_11PM = at(16, 23, 0);

    private static ZonedDateTime at(int day, int hour, int minute) {
        return ZonedDateTime.of(LocalDateTime.of(2026, 11, day, hour, minute), ZONE);
    }

    private static Element place(long id, String name, double metersNorth, Map<String, String> tags) {
        return new Element("node/" + id, name, LAT + metersNorth / 111_000.0, LON, tags);
    }

    private static List<Candidate> rank(Mood mood, ZonedDateTime now, Element... places) {
        return SurpriseRanker.rank(List.of(places), mood, LAT, LON, now);
    }

    @Test
    @DisplayName("Fiecare dispoziție cere de la hartă doar tipurile de locuri care i se potrivesc")
    void everyMoodHasFilters() {
        for (Mood mood : Mood.values()) {
            assertThat(SurpriseRanker.filters(mood)).as(mood.name()).isNotEmpty();
        }
        assertThat(SurpriseRanker.filters(Mood.HUNGRY).get("amenity")).contains("restaurant", "cafe");
        assertThat(SurpriseRanker.filters(Mood.HUNGRY).get("shop")).contains("bakery");
        assertThat(SurpriseRanker.filters(Mood.RELAXED).get("leisure")).contains("park", "garden");
        assertThat(SurpriseRanker.filters(Mood.CULTURAL).get("tourism")).contains("museum", "gallery");
    }

    @Test
    @DisplayName("Planul B: termenii de căutare sunt tipurile cu cea mai bună potrivire, fără dubluri și fără „water”")
    void fallbackTerms() {
        assertThat(SurpriseRanker.fallbackTerms(Mood.RELAXED, 3)).containsExactly("park", "garden", "beach");
        assertThat(SurpriseRanker.fallbackTerms(Mood.HUNGRY, 3)).containsExactly("restaurant", "fast food", "food court");
        assertThat(SurpriseRanker.fallbackTerms(Mood.CULTURAL, 2)).containsExactly("museum", "gallery");
        for (Mood mood : Mood.values()) {
            assertThat(SurpriseRanker.fallbackTerms(mood, 50)).as(mood.name()).doesNotContain("water").doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("Un loc se potrivește doar dispoziției lui: parcul e RELAXED, nu HUNGRY")
    void placeMatchesOnlyItsMood() {
        Element park = place(1, "Central Park", 300, Map.of("leisure", "park"));

        assertThat(rank(Mood.RELAXED, MONDAY_NOON, park)).hasSize(1);
        assertThat(rank(Mood.HUNGRY, MONDAY_NOON, park)).isEmpty();
        assertThat(rank(Mood.SOCIAL, MONDAY_NOON, park)).isEmpty();
    }

    @Test
    @DisplayName("Raza de 2 km: 1,9 km intră, 2,1 km nu")
    void radiusIsTwoKilometres() {
        Element near = place(1, "Near Park", 1900, Map.of("leisure", "park"));
        Element far = place(2, "Far Park", 2100, Map.of("leisure", "park"));

        List<Candidate> ranked = rank(Mood.RELAXED, MONDAY_NOON, near, far);

        assertThat(ranked).extracting(c -> c.place().name()).containsExactly("Near Park");
        assertThat(ranked.get(0).distanceMeters()).isBetween(1890, 1910);
    }

    @Test
    @DisplayName("Deschis acum: muzeul închis luni e exclus, cel deschis rămâne cu ora închiderii")
    void closedPlacesAreExcluded() {
        Element closed = place(1, "Closed Museum", 200, Map.of("tourism", "museum", "opening_hours", "Tu-Su 10:00-18:00"));
        Element open = place(2, "Open Museum", 900, Map.of("tourism", "museum", "opening_hours", "Mo-Su 09:00-17:30"));

        List<Candidate> ranked = rank(Mood.CULTURAL, MONDAY_NOON, closed, open);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).place().name()).isEqualTo("Open Museum");
        assertThat(ranked.get(0).openNote()).isEqualTo("Open until 17:30");
    }

    @Test
    @DisplayName("Locul deschis non-stop apare ca „Open now”, nu ca „Open until 00:00”")
    void aroundTheClockIsOpenNow() {
        Element cafe = place(1, "Always Café", 100, Map.of("amenity", "cafe", "opening_hours", "24/7"));

        assertThat(rank(Mood.RELAXED, MONDAY_NOON, cafe).get(0).openNote()).isEqualTo("Open now");
        assertThat(rank(Mood.RELAXED, MONDAY_11PM, cafe).get(0).openNote()).isEqualTo("Open now");
    }

    @Test
    @DisplayName("Ora închiderii la miezul nopții se scrie „midnight”, nu 00:00")
    void closingAtMidnight() {
        Element restaurant = place(1, "Late Restaurant", 100, Map.of("amenity", "restaurant", "opening_hours", "Mo-Su 10:00-24:00"));

        assertThat(rank(Mood.HUNGRY, MONDAY_NOON, restaurant).get(0).openNote()).isEqualTo("Open until midnight");
    }

    @Test
    @DisplayName("Bucătăria apare doar la restaurante; la o cafenea propoziția rămâne simplă")
    void cuisineOnlyForMeals() {
        Element cafe = place(1, "Bean There", 100,
                Map.of("amenity", "cafe", "cuisine", "coffee_shop", "opening_hours", "24/7"));

        assertThat(SurpriseRanker.why(Mood.HUNGRY, rank(Mood.HUNGRY, MONDAY_NOON, cafe).get(0)))
                .startsWith("A café to fix your hunger");
    }

    @Test
    @DisplayName("Un loc care se închide în 10 minute nu e propus")
    void closingSoonIsExcluded() {
        Element cafe = place(1, "Late Café", 100, Map.of("amenity", "cafe", "opening_hours", "Mo-Su 08:00-12:10"));

        assertThat(rank(Mood.HUNGRY, MONDAY_NOON, cafe)).isEmpty();
    }

    @Test
    @DisplayName("Restaurantul fără program nu e propus (poate fi închis); vederea panoramică fără program da")
    void unknownHoursDependOnTheKindOfPlace() {
        Element restaurant = place(1, "Mystery Restaurant", 100, Map.of("amenity", "restaurant"));
        Element viewpoint = place(2, "City Viewpoint", 400, Map.of("tourism", "viewpoint"));

        assertThat(rank(Mood.HUNGRY, MONDAY_NOON, restaurant)).isEmpty();

        List<Candidate> ranked = rank(Mood.CURIOUS, MONDAY_11PM, viewpoint);
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).openNote()).isEqualTo("Open-air, always accessible");
    }

    @Test
    @DisplayName("Parcul fără program e propus ziua, dar nu la 23:00")
    void daytimePlacesWithoutHoursStayOutAtNight() {
        Element park = place(1, "Central Park", 300, Map.of("leisure", "park"));

        assertThat(rank(Mood.RELAXED, MONDAY_NOON, park)).hasSize(1);
        assertThat(rank(Mood.RELAXED, MONDAY_11PM, park)).isEmpty();
        assertThat(rank(Mood.RELAXED, at(16, 6, 59), park)).isEmpty();
        assertThat(rank(Mood.RELAXED, at(16, 7, 0), park)).hasSize(1);
    }

    @Test
    @DisplayName("Program pe care parserul nu-l înțelege = ca lipsa programului; programul clar e mai bun")
    void unparseableHoursAreTreatedAsUnknown() {
        Element seasonal = place(1, "Seasonal Café", 100,
                Map.of("amenity", "cafe", "opening_hours", "Apr-Oct Mo-Su 09:00-19:00"));
        assertThat(rank(Mood.RELAXED, MONDAY_NOON, seasonal)).isEmpty();
    }

    @Test
    @DisplayName("La 23:00 barul deschis până la 02:00 e propus, cel închis la 22:00 nu")
    void nightlifeAtNight() {
        Element openBar = place(1, "Night Bar", 500, Map.of("amenity", "bar", "opening_hours", "Mo-Su 18:00-02:00"));
        Element closedBar = place(2, "Early Bar", 100, Map.of("amenity", "bar", "opening_hours", "Mo-Su 12:00-22:00"));

        List<Candidate> ranked = rank(Mood.SOCIAL, MONDAY_11PM, openBar, closedBar);

        assertThat(ranked).extracting(c -> c.place().name()).containsExactly("Night Bar");
        assertThat(ranked.get(0).openNote()).isEqualTo("Open until 02:00");
    }

    @Test
    @DisplayName("Ordinea: potrivirea cu dispoziția și notorietatea contează, la egalitate câștigă cel mai apropiat")
    void bestPlaceFirst() {
        Map<String, String> plain = Map.of("leisure", "park");
        Element nearPark = place(1, "Near Park", 200, plain);
        Element farPark = place(2, "Far Park", 1500, plain);
        Element famousGarden = place(3, "Famous Garden", 900,
                Map.of("leisure", "garden", "wikidata", "Q1", "wikimedia_commons", "File:Garden.jpg",
                        "website", "https://garden.example"));
        Element cafe = place(4, "Corner Café", 100, Map.of("amenity", "cafe", "opening_hours", "Mo-Su 08:00-20:00"));

        List<Candidate> ranked = rank(Mood.RELAXED, MONDAY_NOON, farPark, nearPark, cafe, famousGarden);

        assertThat(ranked).extracting(c -> c.place().name())
                .containsExactly("Famous Garden", "Near Park", "Corner Café", "Far Park");
    }

    @Test
    @DisplayName("Locul cu program confirmat bate unul identic cu program necunoscut")
    void verifiedHoursBeatUnverified() {
        Element unknown = place(1, "Unlisted Park", 300, Map.of("leisure", "park"));
        Element verified = place(2, "Listed Park", 300, Map.of("leisure", "park", "opening_hours", "Mo-Su 06:00-22:00"));

        assertThat(rank(Mood.RELAXED, MONDAY_NOON, unknown, verified)).extracting(c -> c.place().name())
                .containsExactly("Listed Park", "Unlisted Park");
    }

    @Test
    @DisplayName("Același loc cartat de două ori (clădire și intrare) apare o singură dată")
    void duplicatesAreMerged() {
        Element building = place(1, "National Museum", 300, Map.of("tourism", "museum", "opening_hours", "Mo-Su 09:00-18:00"));
        Element entrance = place(2, "national museum", 320, Map.of("tourism", "museum", "opening_hours", "Mo-Su 09:00-18:00"));
        Element branchAcrossTown = place(3, "National Museum", 1800, Map.of("tourism", "museum", "opening_hours", "Mo-Su 09:00-18:00"));

        assertThat(rank(Mood.CULTURAL, MONDAY_NOON, building, entrance, branchAcrossTown)).hasSize(2);
    }

    @Test
    @DisplayName("Tag cu mai multe valori (bar;pub) se potrivește; fiecare loc primește cea mai bună regulă")
    void multiValueTags() {
        Element pub = place(1, "Irish Pub", 100, Map.of("amenity", "pub;restaurant", "opening_hours", "24/7"));

        List<Candidate> social = rank(Mood.SOCIAL, MONDAY_NOON, pub);
        assertThat(social).hasSize(1);
        assertThat(social.get(0).rule().value()).isEqualTo("pub");   // pub (1.0) beats restaurant (0.5)
    }

    @Test
    @DisplayName("Timp de mers: ~1,3 × distanța la 5 km/h, minim un minut")
    void walkingTime() {
        Element here = place(1, "Right Here", 10, Map.of("leisure", "park"));
        Element km = place(2, "One Km Away", 1000, Map.of("leisure", "park"));

        List<Candidate> ranked = rank(Mood.RELAXED, MONDAY_NOON, here, km);

        assertThat(ranked.stream().filter(c -> c.place().name().equals("Right Here")).findFirst().orElseThrow()
                .walkingMinutes()).isEqualTo(1);
        assertThat(ranked.stream().filter(c -> c.place().name().equals("One Km Away")).findFirst().orElseThrow()
                .walkingMinutes()).isBetween(15, 17);
    }

    @Test
    @DisplayName("Propoziția „de ce” e una singură, menționează tipul locului și distanța pe jos")
    void whySentence() {
        Element museum = place(1, "Art Museum", 500, Map.of("tourism", "museum", "opening_hours", "24/7"));
        Candidate c = rank(Mood.CULTURAL, MONDAY_NOON, museum).get(0);

        String why = SurpriseRanker.why(Mood.CULTURAL, c);

        assertThat(why).startsWith("A museum").contains("minutes away on foot").endsWith(".");
        assertThat(why.chars().filter(ch -> ch == '.').count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Propoziția „de ce” folosește articolul potrivit și bucătăria când se știe")
    void whyArticleAndCuisine() {
        Element ruins = place(1, "Old Ruins", 100, Map.of("historic", "ruins"));
        Element trattoria = place(2, "Da Mario", 100,
                Map.of("amenity", "restaurant", "cuisine", "italian;pizza", "opening_hours", "24/7"));

        assertThat(SurpriseRanker.why(Mood.CURIOUS, rank(Mood.CURIOUS, MONDAY_NOON, ruins).get(0)))
                .startsWith("A ruins");
        assertThat(SurpriseRanker.why(Mood.HUNGRY, rank(Mood.HUNGRY, MONDAY_NOON, trattoria).get(0)))
                .startsWith("An italian restaurant to fix your hunger");

        Element gallery = place(3, "Modern Gallery", 100, Map.of("tourism", "gallery", "opening_hours", "24/7"));
        assertThat(SurpriseRanker.why(Mood.CULTURAL, rank(Mood.CULTURAL, MONDAY_NOON, gallery).get(0)))
                .startsWith("A gallery");
        Element archaeological = place(4, "Dig Site", 100, Map.of("historic", "archaeological_site"));
        assertThat(SurpriseRanker.why(Mood.CURIOUS, rank(Mood.CURIOUS, MONDAY_NOON, archaeological).get(0)))
                .startsWith("An archaeological site");
    }

    @Test
    @DisplayName("Poza: tag-ul image, altfel fișierul de pe Wikimedia Commons, altfel nimic")
    void photoUrl() {
        assertThat(SurpriseRanker.photoUrl(Map.of("image", "https://img.example/a.jpg")))
                .isEqualTo("https://img.example/a.jpg");
        assertThat(SurpriseRanker.photoUrl(Map.of("wikimedia_commons", "File:Piața Unirii 1.jpg")))
                .isEqualTo("https://commons.wikimedia.org/wiki/Special:FilePath/Pia%C8%9Ba_Unirii_1.jpg?width=800");
        assertThat(SurpriseRanker.photoUrl(Map.of("wikimedia_commons", "Category:Cluj"))).isNull();
        assertThat(SurpriseRanker.photoUrl(Map.of("image", "javascript:alert(1)"))).isNull();
        // Plain http is refused: any mapper can put any host here, and it would not even be encrypted.
        assertThat(SurpriseRanker.photoUrl(Map.of("image", "http://img.example/a.jpg"))).isNull();
        assertThat(SurpriseRanker.photoUrl(Map.of("image", "http://img.example/a.jpg",
                "wikimedia_commons", "File:A.jpg"))).startsWith("https://commons.wikimedia.org/");
        assertThat(SurpriseRanker.photoUrl(Map.of("image", "https://x.example/has space.jpg"))).isNull();
        assertThat(SurpriseRanker.photoUrl(Map.of())).isNull();
    }

    @Test
    @DisplayName("Adresa și site-ul: doar când există și site-ul doar http(s)")
    void addressAndWebsite() {
        assertThat(SurpriseRanker.address(Map.of("addr:street", "Strada Memorandumului", "addr:housenumber", "21")))
                .isEqualTo("Strada Memorandumului 21");
        assertThat(SurpriseRanker.address(Map.of("addr:street", "Strada Memorandumului")))
                .isEqualTo("Strada Memorandumului");
        assertThat(SurpriseRanker.address(Map.of("addr:housenumber", "21"))).isNull();
        assertThat(SurpriseRanker.website(Map.of("contact:website", "https://a.example"))).isEqualTo("https://a.example");
        assertThat(SurpriseRanker.website(Map.of("website", "ftp://a.example"))).isNull();
    }

    @Test
    @DisplayName("Nicio dispoziție nu propune un loc fără potrivire; lista goală dă rezultat gol")
    void nothingNearby() {
        assertThat(SurpriseRanker.rank(List.of(), Mood.HUNGRY, LAT, LON, MONDAY_NOON)).isEmpty();
        Element shop = place(1, "Hardware Store", 100, Map.of("shop", "doityourself"));
        for (Mood mood : Mood.values()) {
            assertThat(rank(mood, MONDAY_NOON, shop)).as(mood.name()).isEmpty();
        }
    }
}
