package com.wanderly.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Thin wrapper around the Gemini generateContent REST endpoint. */
@Component
public class GeminiClient {

    private final String apiKey;
    private final String model;
    private final RestClient http;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model:gemini-3.5-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(f)
                .build();
    }

    /** Sends a prompt and returns the model's JSON text. Retries briefly when the model is busy. */
    @SuppressWarnings("unchecked")
    public String generateJson(String prompt) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key is not set (secrets.properties)");
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));

        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Map<String, Object> res = http.post()
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
            } catch (HttpServerErrorException | HttpClientErrorException.TooManyRequests e) {
                last = e;  // 5xx or 429: model busy, wait and retry
                try { Thread.sleep(1500L * attempt); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("Gemini unavailable, try again", last);
    }
}