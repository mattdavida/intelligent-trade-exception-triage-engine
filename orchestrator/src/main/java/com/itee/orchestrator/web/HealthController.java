package com.itee.orchestrator.web;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itee.orchestrator.ai.AiEngineClient;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final AiEngineClient aiEngineClient;

    public HealthController(DataSource dataSource, AiEngineClient aiEngineClient) {
        this.dataSource = dataSource;
        this.aiEngineClient = aiEngineClient;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("db", dbUp() ? "UP" : "DOWN");
        body.put("aiEngine", aiEngineClient.healthCheck() ? "UP" : "DOWN");
        return body;
    }

    private boolean dbUp() {
        try (var conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
