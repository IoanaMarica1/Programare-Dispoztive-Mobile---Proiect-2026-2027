package com.wanderly.backend.service;

import com.wanderly.backend.service.OpeningHours.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;

class OpeningHoursTest {

    private static Result check(String spec, DayOfWeek day, String from, String to) {
        return OpeningHours.check(spec, day, minutes(from), minutes(to));
    }

    private static int minutes(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    @Test
    @DisplayName("Muzeu Tu-Su: luni e închis, marți în program e deschis")
    void weekdayRange() {
        String spec = "Tu-Su 10:00-18:00";
        assertThat(check(spec, MONDAY, "10:00", "12:00")).isEqualTo(Result.CLOSED);
        assertThat(check(spec, TUESDAY, "10:00", "12:00")).isEqualTo(Result.OPEN);
        assertThat(check(spec, TUESDAY, "08:00", "10:00")).isEqualTo(Result.CLOSED);
        assertThat(check(spec, TUESDAY, "17:00", "18:30")).isEqualTo(Result.CLOSED);
    }

    @Test
    @DisplayName("Mai multe reguli, pauză de prânz și zi off")
    void severalRules() {
        String spec = "Mo-Fr 09:00-12:00,14:00-18:00; Sa 10:00-14:00; Su off";
        assertThat(check(spec, MONDAY, "09:30", "11:30")).isEqualTo(Result.OPEN);
        assertThat(check(spec, MONDAY, "11:30", "14:30")).isEqualTo(Result.CLOSED);
        assertThat(check(spec, SATURDAY, "10:00", "14:00")).isEqualTo(Result.OPEN);
        assertThat(check(spec, SUNDAY, "10:00", "11:00")).isEqualTo(Result.CLOSED);
    }

    @Test
    @DisplayName("Virgulă înaintea unei zile = regulă nouă; regulile ulterioare suprascriu")
    void commaRulesAndOverride() {
        assertThat(check("Mo-Fr 09:00-18:00, Sa 10:00-14:00", SATURDAY, "10:00", "11:00"))
                .isEqualTo(Result.OPEN);
        assertThat(check("Mo-Su 09:00-18:00; Fr 09:00-12:00", FRIDAY, "13:00", "14:00"))
                .isEqualTo(Result.CLOSED);
    }

    @Test
    @DisplayName("Regula adăugată cu virgulă completează ziua, nu o înlocuiește")
    void additionalRuleAddsHours() {
        String spec = "Mo-Fr 09:00-12:00, Mo 14:00-18:00";
        assertThat(check(spec, MONDAY, "10:00", "11:00")).isEqualTo(Result.OPEN);
        assertThat(check(spec, MONDAY, "15:00", "16:00")).isEqualTo(Result.OPEN);
        assertThat(check(spec, TUESDAY, "15:00", "16:00")).isEqualTo(Result.CLOSED);
    }

    @Test
    @DisplayName("Fără zile = în fiecare zi; 24/7; program peste miezul nopții; interval Fr-Mo")
    void otherForms() {
        assertThat(check("10:00-22:00", SUNDAY, "20:00", "22:00")).isEqualTo(Result.OPEN);
        assertThat(check("24/7", MONDAY, "03:00", "04:00")).isEqualTo(Result.OPEN);
        assertThat(check("Mo-Su 18:00-02:00", FRIDAY, "22:00", "23:59")).isEqualTo(Result.OPEN);
        assertThat(check("Fr-Mo 10:00-16:00", SUNDAY, "10:00", "11:00")).isEqualTo(Result.OPEN);
        assertThat(check("Fr-Mo 10:00-16:00", TUESDAY, "10:00", "11:00")).isEqualTo(Result.CLOSED);
        assertThat(check("Mo-Fr 09:00-17:00; PH off", MONDAY, "09:00", "10:00")).isEqualTo(Result.OPEN);
    }

    @Test
    @DisplayName("earliestStart: așteaptă deschiderea, sare peste pauza de prânz, -1 când nu încape")
    void earliestStart() {
        String spec = "Mo-Fr 09:00-12:00,14:00-18:00; Su off";
        assertThat(OpeningHours.earliestStart(spec, MONDAY, minutes("08:00"), 60)).isEqualTo(minutes("09:00"));
        assertThat(OpeningHours.earliestStart(spec, MONDAY, minutes("11:30"), 60)).isEqualTo(minutes("14:00"));
        assertThat(OpeningHours.earliestStart(spec, MONDAY, minutes("17:30"), 60)).isEqualTo(-1);
        assertThat(OpeningHours.earliestStart(spec, SUNDAY, minutes("10:00"), 30)).isEqualTo(-1);
        assertThat(OpeningHours.earliestStart(null, SUNDAY, minutes("10:00"), 30)).isEqualTo(minutes("10:00"));
        assertThat(OpeningHours.earliestStart("24/7", SUNDAY, minutes("03:00"), 30)).isEqualTo(minutes("03:00"));
    }

    @Test
    @DisplayName("Ce nu înțelege parserul dă UNKNOWN, ca stopul să nu fie aruncat pe nedrept")
    void unknownForms() {
        assertThat(check(null, MONDAY, "10:00", "11:00")).isEqualTo(Result.UNKNOWN);
        assertThat(check("Apr-Oct Mo-Su 09:00-19:00", MONDAY, "10:00", "11:00")).isEqualTo(Result.UNKNOWN);
        assertThat(check("Mo-Fr sunrise-sunset", MONDAY, "10:00", "11:00")).isEqualTo(Result.UNKNOWN);
        assertThat(check("Mo-Fr 09:00-17:00 \"by appointment\"", MONDAY, "10:00", "11:00"))
                .isEqualTo(Result.UNKNOWN);
        assertThat(check("PH off", MONDAY, "10:00", "11:00")).isEqualTo(Result.UNKNOWN);
    }
}
