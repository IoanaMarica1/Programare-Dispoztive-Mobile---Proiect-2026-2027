package com.wanderly.backend.dto.trip;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateTripRequestTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static GenerateTripRequest request(String destination, LocalDate start, LocalDate end,
                                               LocalTime dayStart, LocalTime dayEnd) {
        return new GenerateTripRequest(destination, "France", null, null, start, end, dayStart, dayEnd, null);
    }

    private static Set<String> invalidFields(GenerateTripRequest req) {
        return validator.validate(req).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Trip valid de 14 zile, care începe azi → fără erori")
    void valid() {
        GenerateTripRequest req = request("Paris", TODAY, TODAY.plusDays(13), NINE, SIX_PM);

        assertThat(invalidFields(req)).isEmpty();
        assertThat(req.days()).isEqualTo(14);
    }

    @Test
    @DisplayName("Dată în trecut, final înainte de start, peste 14 zile → 400 cu câmpul potrivit")
    void invalidDates() {
        assertThat(invalidFields(request("Paris", TODAY.minusDays(5), TODAY, NINE, SIX_PM)))
                .containsExactly("startDateNotPast");
        assertThat(invalidFields(request("Paris", TODAY.plusDays(5), TODAY.plusDays(1), NINE, SIX_PM)))
                .containsExactly("endDateNotBeforeStart");
        assertThat(invalidFields(request("Paris", TODAY, TODAY.plusDays(14), NINE, SIX_PM)))
                .containsExactly("withinMaxDays");
    }

    @Test
    @DisplayName("Ziua se termină înainte să înceapă sau ține sub o oră → invalid")
    void invalidDailyWindow() {
        assertThat(invalidFields(request("Paris", TODAY, TODAY, SIX_PM, NINE)))
                .containsExactly("dailyWindowValid");
        assertThat(invalidFields(request("Paris", TODAY, TODAY, NINE, LocalTime.of(9, 30))))
                .containsExactly("dailyWindowValid");
    }

    @Test
    @DisplayName("Destinație mai lungă de 100 de caractere → invalid, nu 409 la salvare")
    void destinationTooLong() {
        assertThat(invalidFields(request("x".repeat(101), TODAY, TODAY, NINE, SIX_PM)))
                .containsExactly("destination");
    }
}
