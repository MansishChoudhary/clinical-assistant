package com.clinicalassistant.api;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeNoteRequest(@NotBlank String text) {
}
