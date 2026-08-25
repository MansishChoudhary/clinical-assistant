package com.clinicalassistant.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinical-assistant.ingestion")
public record IngestionProperties(String catalogPath, String guidelinePath) {
}
