package com.clinicalassistant.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GuidelineSnippet(
        @JsonProperty("id") String id,
        @JsonProperty("linked_codes") List<String> linkedCodes,
        @JsonProperty("text") String text) {
}
