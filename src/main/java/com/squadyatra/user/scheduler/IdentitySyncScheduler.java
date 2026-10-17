package com.squadyatra.user.scheduler;

import java.time.Instant;

import com.squadyatra.user.repository.IdentitySyncRepository;
import com.squadyatra.user.service.IdentitySyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "squadyatra.identity-sync.enabled", havingValue = "true", matchIfMissing = true)
public class IdentitySyncScheduler {
    private final IdentitySyncRepository sync;
    private final IdentitySyncService processor;

    public IdentitySyncScheduler(IdentitySyncRepository sync, IdentitySyncService processor) {
        this.sync = sync;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${squadyatra.identity-sync.delay-ms:10000}")
    public void synchronize() {
        for (var userId : sync.due(Instant.now(), PageRequest.of(0, 20))) {
            processor.process(userId);
        }
    }
}