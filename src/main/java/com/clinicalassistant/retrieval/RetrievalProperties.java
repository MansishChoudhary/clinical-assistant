package com.clinicalassistant.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinical-assistant.retrieval")
public record RetrievalProperties(int topK, double similarityThreshold) {

    public RetrievalProperties {
        if (topK <= 0) {
            topK = 5;
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            similarityThreshold = 0.5;
        }
    }
}
