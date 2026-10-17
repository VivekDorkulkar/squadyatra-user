package com.squadyatra.user.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_audit")
public class AuditEntry {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID actorId;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 50)
    private String action;
    @Column(length = 300)
    private String reason;
    @Column(nullable = false, length = 64)
    private String correlationId;
    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditEntry() {
    }

    public AuditEntry(UUID actorId, UUID userId, String action, String reason, String correlationId) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.userId = userId;
        this.action = action;
        this.reason = reason;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
