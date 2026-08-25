package com.clinicalassistant.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CodingAuditLogRepository extends JpaRepository<CodingAuditLog, UUID> {
}
