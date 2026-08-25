package com.clinicalassistant.domain;

import java.util.List;

public record EvidenceItem(
        EvidenceSourceType sourceType,
        String sourceId,
        List<String> relatedCodes,
        String title,
        String content,
        double score) {
}
