package com.clinicalassistant.audit;

import com.clinicalassistant.domain.CodeProposal;
import com.clinicalassistant.domain.VerificationResult;

public record IterationRecord(int iteration, CodeProposal proposal, VerificationResult verification) {
}
