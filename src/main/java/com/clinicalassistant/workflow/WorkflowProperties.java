package com.clinicalassistant.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinical-assistant.workflow")
public record WorkflowProperties(int maxIterations) {

    public WorkflowProperties {
        if (maxIterations <= 0) {
            maxIterations = 3;
        }
    }
}
