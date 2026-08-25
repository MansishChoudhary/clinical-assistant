package com.clinicalassistant.domain;

public record ClinicalNote(String text) {

    public ClinicalNote {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Clinical note text must not be blank");
        }
    }
}
