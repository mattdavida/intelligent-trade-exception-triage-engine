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
        boolean db = dbUp();
        boolean ai = aiEngineClient.healthCheck();

        String status;
        if (db && ai) {
            status = "UP";
        } else if (db || ai) {
            status = "DEGRADED";
        } else {
            status = "DOWN";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("db", db ? "UP" : "DOWN");
        body.put("aiEngine", ai ? "UP" : "DOWN");
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
