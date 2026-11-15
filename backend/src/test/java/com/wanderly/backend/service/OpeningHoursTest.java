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

    private static OpeningHours.Status now(String spec, DayOfWeek day, String time) {
        return OpeningHours.statusAt(spec, day, minutes(time), 30);
    }

    @Test
    @DisplayName("statusAt: deschis acum, cu ora închiderii; închis în afara programului și în ziua liberă")
    void statusAt_openAndClosed() {
        String spec = "Tu-Su 10:00-18:00";
        assertThat(now(spec, TUESDAY, "12:00")).isEqualTo(new OpeningHours.Status(Result.OPEN, minutes("18:00")));
        assertThat(now(spec, TUESDAY, "09:59").result()).isEqualTo(Result.CLOSED);
        assertThat(now(spec, TUESDAY, "18:00").result()).isEqualTo(Result.CLOSED);
        assertThat(now(spec, MONDAY, "12:00").result()).isEqualTo(Result.CLOSED);
    }

    @Test
    @DisplayName("statusAt: nu propune un loc care se închide în mai puțin de 30 de minute")
    void statusAt_closingSoonIsClosed() {
        String spec = "Mo-Su 09:00-18:00";
        assertThat(now(spec, MONDAY, "17:30").result()).isEqualTo(Result.OPEN);
        assertThat(now(spec, MONDAY, "17:31").result()).isEqualTo(Result.CLOSED);
    }

    @Test
    @DisplayName("statusAt: pauza de prânz — închis între intervale, deschis din nou după")
    void statusAt_lunchBreak() {
        String spec = "Mo-Fr 09:00-12:00,14:00-18:00";
        assertThat(now(spec, MONDAY, "13:00").result()).isEqualTo(Result.CLOSED);
        assertThat(now(spec, MONDAY, "14:00")).isEqualTo(new OpeningHours.Status(Result.OPEN, minutes("18:00")));
    }

    @Test
    @DisplayName("statusAt: programul care trece de miezul nopții ține și dimineața următoare (Fr 20:00-02:00)")
    void statusAt_pastMidnight() {
        String spec = "Fr 20:00-02:00";
        assertThat(now(spec, FRIDAY, "23:00")).isEqualTo(new OpeningHours.Status(Result.OPEN, 26 * 60));
        assertThat(now(spec, SATURDAY, "01:00")).isEqualTo(new OpeningHours.Status(Result.OPEN, 2 * 60));
        assertThat(now(spec, SATURDAY, "01:45").result()).isEqualTo(Result.CLOSED);   // closes in 15 min
        assertThat(now(spec, SATURDAY, "20:00").result()).isEqualTo(Result.CLOSED);   // only Fridays
    }

    @Test
    @DisplayName("statusAt: 24/7 și 00:00-24:00 rămân deschise și în ultimele minute dinaintea miezului nopții")
    void statusAt_aroundMidnight() {
        assertThat(now("24/7", SUNDAY, "23:50").result()).isEqualTo(Result.OPEN);
        assertThat(now("Mo-Su 00:00-24:00", MONDAY, "23:45").result()).isEqualTo(Result.OPEN);
        // Closes at midnight and doesn't reopen: 23:45 is too late.
        assertThat(now("Mo-Fr 08:00-24:00", MONDAY, "23:45").result()).isEqualTo(Result.CLOSED);
        // ... but it does continue when the next day opens at 00:00.
        assertThat(now("Mo 12:00-24:00; Tu 00:00-06:00", MONDAY, "23:45").result()).isEqualTo(Result.OPEN);
    }

    @Test
    @DisplayName("statusAt: ce nu înțelege parserul sau lipsa programului dă UNKNOWN")
    void statusAt_unknown() {
        assertThat(now(null, MONDAY, "12:00").result()).isEqualTo(Result.UNKNOWN);
        assertThat(now("", MONDAY, "12:00").result()).isEqualTo(Result.UNKNOWN);
        assertThat(now("Apr-Oct Mo-Su 09:00-19:00", MONDAY, "12:00").result()).isEqualTo(Result.UNKNOWN);
    }
}
