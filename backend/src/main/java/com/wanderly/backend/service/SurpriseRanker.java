package com.wanderly.backend.service;

import com.wanderly.backend.model.Mood;
import com.wanderly.backend.service.OpeningHours.Result;
import com.wanderly.backend.service.OverpassClient.Element;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks and orders the places Surprise Me can suggest: right kind for the mood, within 2 km, open now.
 * Pure functions of their arguments (the time is passed in), so every rule is easy to test.
 *
 * <p>OpenStreetMap has no ratings, so "best" combines how well the kind of place fits the mood, how
 * well documented it is (Wikipedia/Wikidata, photo, website... a stand-in for popularity) and how close it is.
 */
final class SurpriseRanker {

    static final int RADIUS_METERS = 2000;
    /** A place that closes sooner than this isn't worth walking to. */
    static final int MIN_OPEN_MINUTES = 30;

    /** Places with no hours are judged by what they are: a viewpoint is always there, a church is a daytime place. */
    enum Access { ALWAYS, DAYTIME, HOURS }

    /** OSM tag {@code key=value} that makes a place suit a mood; fit is 0..1. */
    record Rule(String key, String value, String label, double fit, Access access) {}

    record Candidate(Element place, Rule rule, int distanceMeters, int walkingMinutes, double score, String openNote) {}

    private static final double WALK_METERS_PER_MINUTE = 83;   // 5 km/h
    private static final double WALK_DETOUR = 1.3;             // streets are longer than the straight line
    private static final int DAYTIME_FROM = 7;
    private static final int DAYTIME_UNTIL = 21;
    private static final double UNVERIFIED_PENALTY = 0.05;
    private static final double DUPLICATE_METERS = 150;
    private static final int MAX_PHOTO_URL = 500;

    static final Map<Mood, List<Rule>> RULES = new EnumMap<>(Mood.class);

    private static Rule r(String key, String value, String label, double fit, Access access) {
        return new Rule(key, value, label, fit, access);
    }

    static {
        RULES.put(Mood.CURIOUS, List.of(
                r("tourism", "attraction", "attraction", 0.9, Access.ALWAYS),
                r("historic", "ruins", "ruins", 0.9, Access.ALWAYS),
                r("historic", "archaeological_site", "archaeological site", 0.9, Access.ALWAYS),
                r("historic", "monument", "monument", 0.8, Access.ALWAYS),
                r("historic", "castle", "castle", 0.9, Access.HOURS),
                r("historic", "fort", "fort", 0.8, Access.DAYTIME),
                r("man_made", "lighthouse", "lighthouse", 0.7, Access.ALWAYS),
                r("tourism", "viewpoint", "viewpoint", 0.75, Access.ALWAYS),
                r("tourism", "museum", "museum", 0.8, Access.HOURS),
                r("tourism", "artwork", "public artwork", 0.6, Access.ALWAYS),
                r("historic", "memorial", "memorial", 0.5, Access.ALWAYS),
                r("tourism", "aquarium", "aquarium", 0.7, Access.HOURS),
                r("tourism", "zoo", "zoo", 0.7, Access.HOURS),
                r("tourism", "gallery", "gallery", 0.5, Access.HOURS)));
        RULES.put(Mood.RELAXED, List.of(
                r("leisure", "park", "park", 1.0, Access.DAYTIME),
                r("leisure", "garden", "garden", 1.0, Access.DAYTIME),
                r("natural", "beach", "beach", 0.9, Access.ALWAYS),
                r("leisure", "nature_reserve", "nature reserve", 0.9, Access.DAYTIME),
                r("natural", "water", "lake", 0.8, Access.ALWAYS),
                r("amenity", "cafe", "café", 0.8, Access.HOURS),
                r("tourism", "viewpoint", "viewpoint", 0.7, Access.ALWAYS),
                r("amenity", "library", "library", 0.5, Access.HOURS)));
        RULES.put(Mood.SOCIAL, List.of(
                r("amenity", "bar", "bar", 1.0, Access.HOURS),
                r("amenity", "pub", "pub", 1.0, Access.HOURS),
                r("amenity", "biergarten", "beer garden", 1.0, Access.HOURS),
                r("amenity", "nightclub", "club", 0.7, Access.HOURS),
                r("amenity", "food_court", "food court", 0.8, Access.HOURS),
                r("amenity", "marketplace", "market", 0.8, Access.DAYTIME),
                r("leisure", "bowling_alley", "bowling alley", 0.7, Access.HOURS),
                r("amenity", "cafe", "café", 0.6, Access.HOURS),
                r("amenity", "restaurant", "restaurant", 0.5, Access.HOURS),
                r("amenity", "arts_centre", "arts centre", 0.4, Access.HOURS)));
        RULES.put(Mood.ADVENTUROUS, List.of(
                r("natural", "peak", "peak", 1.0, Access.ALWAYS),
                r("natural", "cave_entrance", "cave", 1.0, Access.ALWAYS),
                r("waterway", "waterfall", "waterfall", 1.0, Access.ALWAYS),
                r("sport", "climbing", "climbing spot", 0.9, Access.HOURS),
                r("leisure", "nature_reserve", "nature reserve", 0.8, Access.DAYTIME),
                r("tourism", "viewpoint", "viewpoint", 0.8, Access.ALWAYS),
                r("leisure", "water_park", "water park", 0.8, Access.HOURS),
                r("tourism", "theme_park", "theme park", 0.8, Access.HOURS),
                r("leisure", "marina", "marina", 0.6, Access.DAYTIME),
                r("amenity", "bicycle_rental", "bike rental", 0.6, Access.HOURS),
                r("natural", "beach", "beach", 0.5, Access.ALWAYS),
                r("historic", "ruins", "ruins", 0.5, Access.ALWAYS),
                r("leisure", "park", "park", 0.4, Access.DAYTIME)));
        RULES.put(Mood.HUNGRY, List.of(
                r("amenity", "restaurant", "restaurant", 1.0, Access.HOURS),
                r("amenity", "fast_food", "fast-food spot", 0.8, Access.HOURS),
                r("amenity", "food_court", "food court", 0.8, Access.HOURS),
                r("amenity", "cafe", "café", 0.7, Access.HOURS),
                r("shop", "bakery", "bakery", 0.7, Access.HOURS),
                r("shop", "pastry", "pastry shop", 0.7, Access.HOURS),
                r("amenity", "ice_cream", "ice-cream shop", 0.6, Access.HOURS),
                r("amenity", "marketplace", "market", 0.6, Access.DAYTIME),
                r("amenity", "pub", "pub", 0.5, Access.HOURS),
                r("amenity", "biergarten", "beer garden", 0.5, Access.HOURS),
                r("shop", "deli", "deli", 0.5, Access.HOURS)));
        RULES.put(Mood.CULTURAL, List.of(
                r("tourism", "museum", "museum", 1.0, Access.HOURS),
                r("tourism", "gallery", "gallery", 0.95, Access.HOURS),
                r("amenity", "theatre", "theatre", 0.9, Access.HOURS),
                r("historic", "castle", "castle", 0.9, Access.HOURS),
                r("historic", "archaeological_site", "archaeological site", 0.9, Access.ALWAYS),
                r("amenity", "arts_centre", "arts centre", 0.85, Access.HOURS),
                r("historic", "monument", "monument", 0.8, Access.ALWAYS),
                r("historic", "ruins", "ruins", 0.8, Access.ALWAYS),
                r("amenity", "place_of_worship", "place of worship", 0.7, Access.DAYTIME),
                r("tourism", "artwork", "public artwork", 0.6, Access.ALWAYS),
                r("historic", "memorial", "memorial", 0.6, Access.ALWAYS)));
    }

    private SurpriseRanker() {}

    /** The tag filters to ask OpenStreetMap for: key → every value that suits the mood. */
    static Map<String, Set<String>> filters(Mood mood) {
        Map<String, Set<String>> filters = new LinkedHashMap<>();
        for (Rule rule : RULES.get(mood)) {
            filters.computeIfAbsent(rule.key(), k -> new LinkedHashSet<>()).add(rule.value());
        }
        return filters;
    }

    /**
     * What to search Nominatim for when Overpass is down: the kinds that fit the mood best, as plain words
     * ("park", "archaeological site"). Nominatim answers one search per second, so only the best few.
     */
    static List<String> fallbackTerms(Mood mood, int max) {
        return RULES.get(mood).stream()
                .filter(rule -> !rule.value().equals("water"))   // "water" would match every name containing it
                .sorted(Comparator.comparingDouble(Rule::fit).reversed())
                .map(rule -> rule.value().replace('_', ' '))
                .distinct()
                .limit(max)
                .toList();
    }

    /**
     * Every suitable place within 2 km of the traveler that is open now (or, with no usable hours, is the
     * kind of place that is open to everyone at this hour), best first.
     */
    static List<Candidate> rank(List<Element> places, Mood mood, double lat, double lon, ZonedDateTime now) {
        List<Candidate> all = new ArrayList<>();
        for (Element place : places) {
            Candidate c = candidate(place, mood, lat, lon, now);
            if (c != null) all.add(c);
        }
        all.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparingInt(Candidate::distanceMeters)
                .thenComparing(c -> c.place().ref()));

        // OSM often maps one place twice (the building and its entrance): keep the better one.
        List<Candidate> kept = new ArrayList<>();
        outer:
        for (Candidate c : all) {
            for (Candidate k : kept) {
                if (k.place().name().equalsIgnoreCase(c.place().name())
                        && Geo.distanceMeters(k.place().lat(), k.place().lon(), c.place().lat(), c.place().lon())
                        < DUPLICATE_METERS) {
                    continue outer;
                }
            }
            kept.add(c);
        }
        return kept;
    }

    private static Candidate candidate(Element place, Mood mood, double lat, double lon, ZonedDateTime now) {
        int distance = (int) Math.round(Geo.distanceMeters(lat, lon, place.lat(), place.lon()));
        if (distance > RADIUS_METERS) return null;

        Rule rule = bestRule(place.tags(), mood);
        if (rule == null) return null;

        String openNote;
        boolean verified = false;
        String hours = place.tags().get("opening_hours");
        OpeningHours.Status status = OpeningHours.statusAt(hours, now.getDayOfWeek(),
                now.getHour() * 60 + now.getMinute(), MIN_OPEN_MINUTES);
        if (status.result() == Result.CLOSED) return null;
        if (status.result() == Result.OPEN) {
            verified = true;
            int untilClose = status.closesAt() - (now.getHour() * 60 + now.getMinute());
            int closing = status.closesAt() % 1440;
            openNote = untilClose > 18 * 60 ? "Open now"
                    : closing == 0 ? "Open until midnight"
                    : String.format(Locale.ROOT, "Open until %02d:%02d", closing / 60, closing % 60);
        } else {
            boolean daytime = now.getHour() >= DAYTIME_FROM && now.getHour() < DAYTIME_UNTIL;
            switch (rule.access()) {
                case ALWAYS -> openNote = "Open-air, always accessible";
                case DAYTIME -> {
                    if (!daytime) return null;
                    openNote = "Hours not listed, usually open in daytime";
                }
                default -> {
                    return null;  // a restaurant or museum with no usable hours may well be closed
                }
            }
        }

        double proximity = 1.0 - (double) distance / RADIUS_METERS;
        double score = 0.45 * rule.fit() + 0.30 * prominence(place.tags()) + 0.25 * proximity
                - (verified ? 0 : UNVERIFIED_PENALTY);
        int walking = Math.max(1, (int) Math.ceil(distance * WALK_DETOUR / WALK_METERS_PER_MINUTE));
        return new Candidate(place, rule, distance, walking, score, openNote);
    }

    /** The rule of the mood that matches the place best (a tag may list several values, "bar;pub"). */
    private static Rule bestRule(Map<String, String> tags, Mood mood) {
        Rule best = null;
        for (Rule rule : RULES.get(mood)) {
            String value = tags.get(rule.key());
            if (value == null) continue;
            for (String v : value.split(";")) {
                if (v.trim().equals(rule.value()) && (best == null || rule.fit() > best.fit())) best = rule;
            }
        }
        return best;
    }

    /** 0..1: how well documented a place is on OpenStreetMap, standing in for how popular it is. */
    static double prominence(Map<String, String> tags) {
        double p = 0;
        if (tags.containsKey("wikidata") || tags.containsKey("wikipedia")) p += 0.35;
        if (tags.containsKey("image") || tags.containsKey("wikimedia_commons")) p += 0.15;
        if (tags.containsKey("website") || tags.containsKey("contact:website")) p += 0.10;
        if (tags.containsKey("heritage")) p += 0.10;
        if ("attraction".equals(tags.get("tourism"))) p += 0.10;
        if (tags.containsKey("stars") || tags.containsKey("michelin_stars")) p += 0.10;
        if (tags.containsKey("opening_hours")) p += 0.05;
        if (tags.containsKey("phone") || tags.containsKey("contact:phone")) p += 0.05;
        if (tags.containsKey("description")) p += 0.05;
        return Math.min(1.0, p);
    }

    /** One friendly sentence on why this place fits the mood. */
    static String why(Mood mood, Candidate c) {
        String kind = c.rule().label();
        String walk = c.walkingMinutes() <= 1 ? "about a minute away on foot"
                : c.walkingMinutes() + " minutes away on foot";
        String a = article(kind);
        String cuisine = firstCuisine(c.place().tags());
        return switch (mood) {
            case CURIOUS -> a + " " + kind + " with a story to discover, " + walk + ".";
            case RELAXED -> "A calm " + kind + " to slow down in, " + walk + ".";
            case SOCIAL -> "A lively " + kind + " where you can meet people, " + walk + ".";
            case ADVENTUROUS -> a + " " + kind + " to get your pulse up, " + walk + ".";
            // "An italian restaurant"; only for places that serve meals ("coffee shop café" would read oddly).
            case HUNGRY -> cuisine != null && c.rule().value().matches("restaurant|fast_food")
                    ? article(cuisine) + " " + cuisine + " " + kind + " to fix your hunger, " + walk + "."
                    : a + " " + kind + " to fix your hunger, " + walk + ".";
            case CULTURAL -> a + " " + kind + " full of local culture, " + walk + ".";
        };
    }

    private static String article(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        return "aeiou".indexOf(w.charAt(0)) >= 0 ? "An" : "A";
    }

    /** "regional;italian" → "regional"; null when absent. */
    private static String firstCuisine(Map<String, String> tags) {
        String cuisine = tags.get("cuisine");
        if (cuisine == null || cuisine.isBlank()) return null;
        String first = cuisine.split(";")[0].trim().replace('_', ' ');
        return first.isEmpty() ? null : first;
    }

    /** A picture of the place: the mapper's own image, else its Wikimedia Commons file; null when neither. */
    static String photoUrl(Map<String, String> tags) {
        String image = tags.get("image");
        // https only: any mapper can put any address in this tag, and a plain-http image would also leak
        // the traveler's address to that host without even being encrypted.
        if (image != null && image.startsWith("https://")
                && image.length() <= MAX_PHOTO_URL && !image.contains(" ")) {
            return image;
        }
        String commons = tags.get("wikimedia_commons");
        if (commons != null && commons.startsWith("File:") && commons.length() > 5) {
            String file = commons.substring(5).trim().replace(' ', '_');
            return "https://commons.wikimedia.org/wiki/Special:FilePath/"
                    + UriUtils.encodePathSegment(file, StandardCharsets.UTF_8) + "?width=800";
        }
        return null;
    }

    static String address(Map<String, String> tags) {
        String street = tags.get("addr:street");
        if (street == null || street.isBlank()) return null;
        String number = tags.get("addr:housenumber");
        return number == null || number.isBlank() ? street.trim() : street.trim() + " " + number.trim();
    }

    static String website(Map<String, String> tags) {
        String site = tags.getOrDefault("website", tags.get("contact:website"));
        return site != null && (site.startsWith("https://") || site.startsWith("http://")) && site.length() <= MAX_PHOTO_URL
                ? site : null;
    }
}
