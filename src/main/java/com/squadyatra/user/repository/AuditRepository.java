package com.squadyatra.user.repository;

import java.util.UUID;

import com.squadyatra.user.entities.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {
}