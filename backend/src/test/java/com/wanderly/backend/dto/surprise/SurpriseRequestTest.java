package com.wanderly.backend.dto.surprise;

import com.wanderly.backend.model.Mood;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SurpriseRequestTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Cerere completă și validă, cu sau fără fus orar și excluderi")
    void valid() {
        assertThat(validator.validate(new SurpriseRequest(Mood.HUNGRY, 46.77, 23.59, "Europe/Bucharest",
                List.of("node/1", "way/22", "relation/333")))).isEmpty();
        assertThat(validator.validate(new SurpriseRequest(Mood.CULTURAL, -33.9, 151.2, null, null))).isEmpty();
    }

    @Test
    @DisplayName("Dispoziția și poziția sunt obligatorii")
    void requiredFields() {
        assertThat(invalidFields(new SurpriseRequest(null, null, null, null, null)))
                .containsExactlyInAnyOrder("mood", "latitude", "longitude");
    }

    @Test
    @DisplayName("Latitudinea în [-90, 90] și longitudinea în [-180, 180]")
    void coordinateRanges() {
        assertThat(invalidFields(new SurpriseRequest(Mood.RELAXED, 90.1, 0.0, null, null))).containsExactly("latitude");
        assertThat(invalidFields(new SurpriseRequest(Mood.RELAXED, 0.0, -180.1, null, null))).containsExactly("longitude");
        assertThat(validator.validate(new SurpriseRequest(Mood.RELAXED, -90.0, 180.0, null, null))).isEmpty();
    }

    @Test
    @DisplayName("Id-urile excluse trebuie să fie referințe OSM (nu se pot strecura în interogarea Overpass)")
    void excludeMustBeOsmReferences() {
        for (String bad : List.of("node/abc", "poi/1", "node/1;out", "node/1\"]", "", "node/", "node/1234567890123")) {
            assertThat(validator.validate(new SurpriseRequest(Mood.RELAXED, 1.0, 1.0, null, List.of(bad))))
                    .as(bad).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Cel mult 50 de locuri excluse")
    void excludeIsBounded() {
        List<String> fifty = new ArrayList<>();
        for (int i = 1; i <= 50; i++) fifty.add("node/" + i);
        assertThat(validator.validate(new SurpriseRequest(Mood.RELAXED, 1.0, 1.0, null, fifty))).isEmpty();

        fifty.add("node/51");
        assertThat(invalidFields(new SurpriseRequest(Mood.RELAXED, 1.0, 1.0, null, fifty))).containsExactly("exclude");
    }

    @Test
    @DisplayName("Check-in: id de loc valid și poziție obligatorie, în limite")
    void checkInRequest() {
        assertThat(validator.validate(new CheckInRequest("node/42", 46.77, 23.59))).isEmpty();
        assertThat(invalidFields(new CheckInRequest("", null, null)))
                .containsExactlyInAnyOrder("placeId", "latitude", "longitude");
        assertThat(invalidFields(new CheckInRequest("node/1;out", 95.0, 0.0)))
                .containsExactlyInAnyOrder("placeId", "latitude");
    }
}
