package com.squadyatra.user.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_sync")
public class IdentitySync {
    @Id
    private UUID userId;
    private int attempts;
    private Instant nextAttemptAt;
    private boolean failed;

    protected IdentitySync() {
    }

    public IdentitySync(UUID userId) {
        this.userId = userId;
        this.nextAttemptAt = Instant.now();
    }

    public void reset() {
        attempts = 0;
        failed = false;
        nextAttemptAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }
}
