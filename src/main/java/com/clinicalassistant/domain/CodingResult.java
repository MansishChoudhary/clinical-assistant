package com.clinicalassistant.domain;

import java.util.List;

public record CodingResult(
        CodingOutcome outcome,
        List<String> proposedCodes,
        RetrievedEvidence evidence,
        ConfidenceLevel confidence,
        String rationale) {

    public static CodingResult noConfidentMatch(RetrievedEvidence evidence, String rationale) {
        return new CodingResult(CodingOutcome.NO_CONFIDENT_MATCH, List.of(), evidence, ConfidenceLevel.LOW, rationale);
    }

    public static CodingResult coded(List<String> proposedCodes, RetrievedEvidence evidence,
            ConfidenceLevel confidence, String rationale) {
        return new CodingResult(CodingOutcome.CODED, proposedCodes, evidence, confidence, rationale);
    }
}
