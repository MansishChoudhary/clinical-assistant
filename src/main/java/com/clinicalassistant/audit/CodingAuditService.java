package com.clinicalassistant.audit;

import com.clinicalassistant.domain.CodingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class CodingAuditService {

    private final CodingAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public CodingAuditService(CodingAuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(String noteText, CodingResult result, List<IterationRecord> iterations) {
        CodingAuditLog log = new CodingAuditLog(
                sha256(noteText),
                result.outcome(),
                String.join(",", result.proposedCodes()),
                result.confidence(),
                toJson(iterations));
        repository.save(log);
    }

    private String toJson(List<IterationRecord> iterations) {
        try {
            return objectMapper.writeValueAsString(iterations);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
