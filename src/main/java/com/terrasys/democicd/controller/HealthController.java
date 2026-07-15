package com.terrasys.democicd.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @Value("${db.host}")
    private String dbHost;

    @Value("${db.database}")
    private String dbName;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "database", dbName,
            "host", dbHost
        );
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
            "app", "demo-cicd",
            "version", "1.0.0",
            "message", "Spring Boot CI/CD Demo is running"
        );
    }
}
