package com.clinicalassistant.domain;

import java.util.List;

public record CodeProposal(List<String> proposedCodes, String rationale, List<String> citedEvidenceIds) {

    public boolean isEmpty() {
        return proposedCodes == null || proposedCodes.isEmpty();
    }
}
