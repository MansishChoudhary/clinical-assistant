package com.clinicalassistant.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IcdCatalogEntry(
        @JsonProperty("code") String code,
        @JsonProperty("title") String title,
        @JsonProperty("chapter") String chapter,
        @JsonProperty("description") String description) {
}
