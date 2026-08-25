package com.clinicalassistant.domain;

import java.util.List;
import java.util.stream.Stream;

public record RetrievedEvidence(List<EvidenceItem> catalogMatches, List<EvidenceItem> guidelineMatches) {

    public List<EvidenceItem> allItems() {
        return Stream.concat(catalogMatches.stream(), guidelineMatches.stream()).toList();
    }

    public boolean isEmpty() {
        return catalogMatches.isEmpty() && guidelineMatches.isEmpty();
    }
}
