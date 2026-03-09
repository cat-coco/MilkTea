package com.milktea.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Frontend version configuration.
 * Controls which frontend extension to use:
 * - v1: frontend-extension (MilkTea order agent, brown theme)
 * - v2: frontend-extensionV2 (Financial report analysis, blue theme)
 *
 * Configure in application.yml:
 *   app.frontend.version: v1 or v2
 */
@Configuration
@RestController
public class FrontendVersionConfig {

    @Value("${app.frontend.version:v1}")
    private String frontendVersion;

    /**
     * Returns the current frontend version configuration.
     * Frontend extensions can call this endpoint to check if they should activate.
     */
    @GetMapping("/api/config/frontend-version")
    public Map<String, String> getFrontendVersion() {
        return Map.of(
                "version", frontendVersion,
                "description", "v1".equals(frontendVersion)
                        ? "MilkTea Order Agent (frontend-extension)"
                        : "Financial Report Analysis (frontend-extensionV2)"
        );
    }

    public String getVersion() {
        return frontendVersion;
    }
}
