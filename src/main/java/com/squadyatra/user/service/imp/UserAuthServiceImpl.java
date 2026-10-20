package com.squadyatra.user.service.imp;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.squadyatra.user.client.SupabaseClient;
import com.squadyatra.user.client.SupabaseSession;
import com.squadyatra.user.dto.LoginRequest;
import com.squadyatra.user.dto.LoginResult;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.RegistrationStatus;
import com.squadyatra.user.repository.UserRepository;
import com.squadyatra.user.service.UserAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAuthServiceImpl implements UserAuthService {
    private final SupabaseClient provider;
    private final UserRepository users;
    private final Map<String, Window> windows = new HashMap<>();

    public UserAuthServiceImpl(SupabaseClient provider, UserRepository users) {
        this.provider = provider;
        this.users = users;
    }

    @Override
    public LoginResult signIn(LoginRequest request, String remoteAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        limit("ip:" + remoteAddress, 20);
        limit("email:" + DigestUtils.md5DigestAsHex(email.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 10);
        SupabaseSession session = provider.signIn(email, request.password());
        if (!session.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Confirm your email before signing in");
        }
        var account = users.findByEmail(email).orElse(null);
        if (account == null || account.getRegistrationStatus() != RegistrationStatus.COMPLETED
                || account.getStatus() != AccountStatus.ACTIVE || account.getSupabaseUserId() == null
                || !account.getSupabaseUserId().equals(session.userId())
                || !account.getEmail().equalsIgnoreCase(session.email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "An active SquadYatra account is required to sign in");
        }
        return session.tokens();
    }

    @Override
    public URI oauthRedirect(URI authorizationUrl) {
        return provider.oauthRedirect(authorizationUrl);
    }

    @Override
    public void updatePassword(UUID userId, String password) {
        provider.updatePassword(userId, password);
    }

    private synchronized void limit(String key, int maximum) {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 10000) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
            }
            window = new Window(0, now.plusSeconds(900));
        }
        if (window.count() >= maximum) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
        }
        windows.put(key, new Window(window.count() + 1, window.expiresAt()));
    }

    private record Window(int count, Instant expiresAt) {
    }
}