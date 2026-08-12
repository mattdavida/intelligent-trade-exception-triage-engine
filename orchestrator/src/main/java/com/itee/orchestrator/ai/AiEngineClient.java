package com.itee.orchestrator.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itee.orchestrator.domain.TradeException;

@Component
public class AiEngineClient {

    private final AiEngineProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiEngineClient(AiEngineProperties properties, ObjectMapper objectMapper) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "itee.ai.api-key / AI_ENGINE_API_KEY is required - load .env via start-orchestrator.ps1");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        // Force HTTP/1.1 — Java's default HTTP/2 upgrade confuses uvicorn (empty body / 422).
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public AiAnalyzeResponse analyze(TradeException ex) {
        AiAnalyzeRequest body = new AiAnalyzeRequest(
                ex.getId(),
                ex.getTradeId(),
                ex.getCounterparty(),
                ex.getDiscrepancyType(),
                ex.getInstrument(),
                ex.getAmount(),
                ex.getCurrency(),
                ex.getSide(),
                ex.getDetectedAt().toString(),
                ex.getRawDetails());

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(properties.baseUrl()) + "/api/v1/analyze-exception"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "AI engine HTTP " + response.statusCode() + ": " + response.body());
            }

            AiAnalyzeResponse parsed =
                    objectMapper.readValue(response.body(), AiAnalyzeResponse.class);
            if (parsed.getSeverity() == null
                    || parsed.getRecommendation() == null
                    || parsed.getReasoning() == null) {
                throw new IllegalStateException("AI engine returned incomplete response");
            }
            return parsed;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("AI engine call failed: " + e.getMessage(), e);
        }
    }

    public boolean healthCheck() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(properties.baseUrl()) + "/api/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:8000";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
