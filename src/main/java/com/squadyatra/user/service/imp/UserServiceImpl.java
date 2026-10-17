package com.squadyatra.user.service.imp;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.squadyatra.user.dto.Deactivation;
import com.squadyatra.user.dto.PageView;
import com.squadyatra.user.dto.ProfileUpdate;
import com.squadyatra.user.dto.StatusChange;
import com.squadyatra.user.dto.TypeChange;
import com.squadyatra.user.dto.UserSummary;
import com.squadyatra.user.dto.UserView;
import com.squadyatra.user.entities.AuditEntry;
import com.squadyatra.user.entities.IdentitySync;
import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.RegistrationStatus;
import com.squadyatra.user.enums.UserType;
import com.squadyatra.user.repository.AuditRepository;
import com.squadyatra.user.repository.IdentitySyncRepository;
import com.squadyatra.user.repository.UserRepository;
import com.squadyatra.user.service.UserService;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository users;
    private final AuditRepository audit;
    private final IdentitySyncRepository sync;

    public UserServiceImpl(UserRepository users, AuditRepository audit, IdentitySyncRepository sync) {
        this.users = users;
        this.audit = audit;
        this.sync = sync;
    }

    @Transactional(readOnly = true)
    @Override
    public UserView me(UUID userId) {
        return UserView.from(active(subject(userId)));
    }

    @Transactional(readOnly = true)
    @Override
    public UserView get(UUID userId, UUID id) {
        UserAccount actor = active(subject(userId));
        if (!actor.getId().equals(id)) {
            admin(actor);
        }
        return UserView.from(find(id));
    }

    @Transactional(readOnly = true)
    @Override
    public PageView<UserSummary> list(UUID userId, AccountStatus status, UserType type, int page, int size) {
        admin(active(subject(userId)));
        if (page < 0 || size < 1 || size > 100) {
            throw problem(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size must be between 1 and 100");
        }
        var result = users.search(status, type, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new PageView<>(result.map(UserSummary::from).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public UserView update(UUID userId, ProfileUpdate request) {
        UserAccount account = locked(subject(userId));
        ensureActive(account);
        version(account, request.version());
        try {
            ZoneId.of(request.timeZone());
            new java.util.Locale.Builder().setLanguageTag(request.locale()).build();
        } catch (RuntimeException exception) {
            throw problem(HttpStatus.BAD_REQUEST, "Invalid time zone or locale");
        }
        account.setFullName(request.fullName().trim());
        account.setPhone(request.phone());
        account.setDateOfBirth(request.dateOfBirth());
        account.setCity(request.city());
        account.setCountryCode(request.countryCode());
        account.setTimeZone(request.timeZone());
        account.setLocale(request.locale());
        account.setBio(request.bio());
        account.setEmailNotifications(request.emailNotifications());
        if (account.isMarketingConsent() != request.marketingConsent()) {
            account.setMarketingConsentUpdatedAt(Instant.now());
        }
        account.setMarketingConsent(request.marketingConsent());
        return changed(account, account.getId(), "PROFILE_UPDATED", null, false);
    }

    @Override
    public UserView deactivate(UUID userId, Deactivation request) {
        UserAccount account = locked(subject(userId));
        ensureActive(account);
        version(account, request.version());
        if (account.getUserType() == UserType.ADMIN) {
            throw problem(HttpStatus.CONFLICT, "Administrators must be deactivated by another administrator");
        }
        account.setStatus(AccountStatus.INACTIVE);
        account.setStatusReason(request.reason());
        return changed(account, account.getId(), "USER_DEACTIVATED", request.reason(), true);
    }

    @Override
    public UserView status(UUID userId, UUID id, StatusChange request) {
        UserAccount actor = active(subject(userId));
        admin(actor);
        if (actor.getId().equals(id)) {
            throw problem(HttpStatus.CONFLICT, "Administrators cannot change their own account status");
        }
        UserAccount account = locked(id);
        ensureRegistered(account);
        version(account, request.version());
        account.setStatus(request.status());
        account.setStatusReason(request.reason());
        return changed(account, actor.getId(), "STATUS_CHANGED", request.reason(), true);
    }

    @Override
    public UserView type(UUID userId, UUID id, TypeChange request) {
        UserAccount actor = active(subject(userId));
        admin(actor);
        if (actor.getId().equals(id)) {
            throw problem(HttpStatus.CONFLICT, "Administrators cannot change their own user type");
        }
        UserAccount account = locked(id);
        ensureRegistered(account);
        version(account, request.version());
        account.setUserType(request.userType());
        return changed(account, actor.getId(), "USER_TYPE_CHANGED", request.reason(), true);
    }

    @Override
    public void retrySync(UUID userId, UUID id) {
        UserAccount actor = active(subject(userId));
        admin(actor);
        ensureRegistered(locked(id));
        enqueue(id);
        record(actor.getId(), id, "IDENTITY_SYNC_RETRIED", null);
    }

    private UserView changed(UserAccount account, UUID actor, String action, String reason, boolean synchronize) {
        account.setUpdatedAt(Instant.now());
        users.flush();
        record(actor, account.getId(), action, reason);
        if (synchronize) {
            enqueue(account.getId());
        }
        return UserView.from(account);
    }

    private void enqueue(UUID id) {
        IdentitySync entry = sync.findById(id).orElseGet(() -> new IdentitySync(id));
        entry.reset();
        sync.save(entry);
    }

    private void record(UUID actor, UUID target, String action, String reason) {
        String correlation = MDC.get("correlationId");
        audit.save(new AuditEntry(actor, target, action, reason,
                correlation == null ? UUID.randomUUID().toString() : correlation));
    }

    private UserAccount active(UUID id) {
        UserAccount account = find(id);
        ensureActive(account);
        return account;
    }

    private void ensureActive(UserAccount account) {
        if (account.getStatus() != AccountStatus.ACTIVE || account.getRegistrationStatus() != RegistrationStatus.COMPLETED) {
            throw problem(HttpStatus.FORBIDDEN, "Account is not active");
        }
    }

    private void ensureRegistered(UserAccount account) {
        if (account.getRegistrationStatus() != RegistrationStatus.COMPLETED) {
            throw problem(HttpStatus.CONFLICT, "Registration must be completed before changing this account");
        }
    }

    private void admin(UserAccount actor) {
        if (actor.getUserType() != UserType.ADMIN) {
            throw problem(HttpStatus.FORBIDDEN, "Administrator access is required");
        }
    }

    private UserAccount find(UUID id) {
        return users.findById(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "User profile not found"));
    }

    private UserAccount locked(UUID id) {
        return users.findForUpdate(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "User profile not found"));
    }

    private void version(UserAccount account, long expected) {
        if (account.getVersion() != expected) {
            throw problem(HttpStatus.CONFLICT, "User was changed; reload the profile and retry");
        }
    }

    private UUID subject(UUID userId) {
        return users.findBySupabaseUserId(userId).map(UserAccount::getId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "No user profile is mapped to this identity"));
    }

    private ResponseStatusException problem(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}