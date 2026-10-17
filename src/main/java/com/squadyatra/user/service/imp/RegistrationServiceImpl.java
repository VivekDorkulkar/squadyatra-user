package com.squadyatra.user.service.imp;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.squadyatra.user.client.SupabaseClient;
import com.squadyatra.user.dto.Registration;
import com.squadyatra.user.dto.RegistrationResult;
import com.squadyatra.user.entities.AuditEntry;
import com.squadyatra.user.entities.IdentitySync;
import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.enums.RegistrationStatus;
import com.squadyatra.user.exceptions.RegistrationRejectedException;
import com.squadyatra.user.repository.AuditRepository;
import com.squadyatra.user.repository.IdentitySyncRepository;
import com.squadyatra.user.repository.UserRepository;
import com.squadyatra.user.service.RegistrationService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    private final UserRepository users;
    private final AuditRepository audit;
    private final IdentitySyncRepository sync;
    private final SupabaseClient provider;
    private final TransactionTemplate transaction;
    private final Map<String, Window> limits = new HashMap<>();

    public RegistrationServiceImpl(UserRepository users, AuditRepository audit, IdentitySyncRepository sync,
                                   SupabaseClient provider, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.audit = audit;
        this.sync = sync;
        this.provider = provider;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RegistrationResult register(Registration request, String remoteAddress) {
        limit(remoteAddress);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UUID localId = transaction.execute(status -> {
            UserAccount account = users.findForUpdateByEmail(email).orElse(null);
            if (account == null) {
                account = UserAccount.pending(UUID.randomUUID(), email, request.fullName().trim(), request.phoneNumber());
            } else if (account.getRegistrationStatus() == RegistrationStatus.FAILED) {
                account.retryRegistration(request.fullName().trim(), request.phoneNumber());
            } else {
                throw new RegistrationRejectedException();
            }
            users.saveAndFlush(account);
            record(account.getId(), "REGISTRATION_REQUESTED");
            return account.getId();
        });

        UUID supabaseId;
        try {
            supabaseId = provider.signUp(email, request.password(), request.fullName().trim(),
                    request.phoneNumber(), request.captchaToken());
        } catch (RegistrationRejectedException exception) {
            transaction.executeWithoutResult(status -> {
                UserAccount account = users.findForUpdate(localId).orElseThrow();
                account.failRegistration();
                record(localId, "REGISTRATION_REJECTED");
            });
            throw exception;
        }

        transaction.executeWithoutResult(status -> {
            UserAccount account = users.findForUpdate(localId).orElseThrow();
            account.completeRegistration(supabaseId);
            users.flush();
            sync.save(new IdentitySync(localId));
            record(localId, "REGISTRATION_COMPLETED");
        });
        return new RegistrationResult(localId, supabaseId,
                "Account created. Check your email if confirmation is required, then sign in");
    }

    private void record(UUID id, String action) {
        String correlation = MDC.get("correlationId");
        audit.save(new AuditEntry(id, id, action, null,
                correlation == null ? UUID.randomUUID().toString() : correlation));
    }

    private synchronized void limit(String peer) {
        Instant now = Instant.now();
        limits.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Window window = limits.get(peer);
        if (window == null) {
            if (limits.size() >= 10000) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
            }
            window = new Window(0, now.plusSeconds(900));
        }
        if (window.count() >= 10) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
        }
        limits.put(peer, new Window(window.count() + 1, window.expiresAt()));
    }

    private record Window(int count, Instant expiresAt) {
    }
}