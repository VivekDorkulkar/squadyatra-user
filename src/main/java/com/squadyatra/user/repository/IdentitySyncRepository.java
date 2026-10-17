package com.squadyatra.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.squadyatra.user.entities.IdentitySync;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IdentitySyncRepository extends JpaRepository<IdentitySync, UUID> {
    @Query("select sync.userId from IdentitySync sync where sync.failed = false "
            + "and sync.nextAttemptAt <= :now order by sync.nextAttemptAt")
    List<UUID> due(Instant now, Pageable pageable);
}