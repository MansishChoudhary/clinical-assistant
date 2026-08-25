package com.clinicalassistant.audit;

import com.clinicalassistant.domain.CodingOutcome;
import com.clinicalassistant.domain.ConfidenceLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coding_audit_log")
public class CodingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String noteHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodingOutcome outcome;

    @Column(nullable = false)
    private String proposedCodes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfidenceLevel confidence;

    @Column(nullable = false, columnDefinition = "text")
    private String iterationsJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CodingAuditLog() {
        // JPA
    }

    public CodingAuditLog(String noteHash, CodingOutcome outcome, String proposedCodes, ConfidenceLevel confidence,
            String iterationsJson) {
        this.noteHash = noteHash;
        this.outcome = outcome;
        this.proposedCodes = proposedCodes;
        this.confidence = confidence;
        this.iterationsJson = iterationsJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNoteHash() {
        return noteHash;
    }

    public CodingOutcome getOutcome() {
        return outcome;
    }

    public String getProposedCodes() {
        return proposedCodes;
    }

    public ConfidenceLevel getConfidence() {
        return confidence;
    }

    public String getIterationsJson() {
        return iterationsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
