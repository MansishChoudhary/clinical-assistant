package com.clinicalassistant.domain;

import java.util.List;

public record VerificationResult(VerificationVerdict verdict, String rationale, List<String> reliedOnEvidenceIds) {
}
