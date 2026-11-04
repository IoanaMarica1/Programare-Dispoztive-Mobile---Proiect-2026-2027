package com.wanderly.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Thin wrapper around the Gemini generateContent REST endpoint. */
@Component
public class GeminiClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BACKOFF = Duration.ofMillis(1500);
    /** A retry is only worth sending if the model gets at least this long to answer. */
    private static final Duration MIN_ATTEMPT = Duration.ofSeconds(10);

    private final String apiKey;
    private final String model;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model:gemini-3.5-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        if (apiKey.isBlank()) {
            org.slf4j.LoggerFactory.getLogger(GeminiClient.class)
                    .warn("gemini.api-key is not set: trip generation will fail (see secrets.properties.example)");
        }
    }

    /**
     * Sends a prompt and returns the model's JSON text. Each attempt may wait for the answer until the
     * deadline. When the model is busy (5xx, 429) or the connection fails, it retries only while a
     * useful attempt still fits before the deadline.
     */
    @SuppressWarnings("unchecked")
    public String generateJson(String prompt, Instant deadline) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key is not set (secrets.properties)");
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "thinkingConfig", Map.of("thinkingLevel", "LOW")));

        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                Duration wait = BACKOFF.multipliedBy(attempt - 1);
                if (Instant.now().plus(wait).plus(MIN_ATTEMPT).isAfter(deadline)) break;
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Duration timeout = Duration.between(Instant.now(), deadline);
            if (timeout.compareTo(MIN_ATTEMPT) < 0) timeout = MIN_ATTEMPT;  // the first attempt always gets a fair try
            try {
                Map<String, Object> res = client(timeout).post()
                        .uri("/models/{model}:generateContent", model)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(Map.class);

                List<Map<String, Object>> candidates = (List<Map<String, Object>>) res.get("candidates");
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                return (String) parts.get(0).get("text");
            } catch (HttpServerErrorException | HttpClientErrorException.TooManyRequests
                     | ResourceAccessException e) {
                last = e;
            }
        }
        throw new IllegalStateException("Gemini unavailable, try again", last);
    }

    private static RestClient client(Duration readTimeout) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(f)
                .build();
    }
}
