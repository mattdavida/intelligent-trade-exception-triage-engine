package com.itee.orchestrator.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "itee.ai")
public record AiEngineProperties(String baseUrl, String apiKey) {}
