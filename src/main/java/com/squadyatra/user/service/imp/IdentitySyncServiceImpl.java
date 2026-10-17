package com.squadyatra.user.service.imp;

import java.time.Instant;
import java.util.UUID;

import com.squadyatra.user.client.SupabaseClient;
import com.squadyatra.user.entities.IdentitySync;
import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.exceptions.IdentityProviderException;
import com.squadyatra.user.repository.IdentitySyncRepository;
import com.squadyatra.user.repository.UserRepository;
import com.squadyatra.user.service.IdentitySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentitySyncServiceImpl implements IdentitySyncService {
    private static final Logger LOG = LoggerFactory.getLogger(IdentitySyncServiceImpl.class);
    private final UserRepository users;
    private final IdentitySyncRepository sync;
    private final SupabaseClient provider;

    public IdentitySyncServiceImpl(UserRepository users, IdentitySyncRepository sync, SupabaseClient provider) {
        this.users = users;
        this.sync = sync;
        this.provider = provider;
    }

    @Transactional
    @Override
    public void process(UUID userId) {
        UserAccount account = users.findForUpdate(userId).orElse(null);
        IdentitySync entry = sync.findById(userId).orElse(null);
        if (account == null || entry == null || entry.isFailed() || entry.getNextAttemptAt().isAfter(Instant.now())) {
            return;
        }
        try {
            provider.synchronize(account.getSupabaseUserId(), account.getUserType().value(), account.getStatus() == AccountStatus.ACTIVE);
            sync.delete(entry);
        } catch (IdentityProviderException exception) {
            entry.setAttempts(entry.getAttempts() + 1);
            entry.setFailed(entry.getAttempts() >= 8);
            entry.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 15L << entry.getAttempts())));
            LOG.warn("Identity synchronization deferred; attempts={}, failed={}", entry.getAttempts(), entry.isFailed());
        }
    }
}