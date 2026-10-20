package com.squadyatra.user.service.imp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.squadyatra.user.client.SupabaseClient;
import com.squadyatra.user.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {
    private final SupabaseClient provider;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    @Autowired
    public PasswordResetServiceImpl(SupabaseClient provider) {
        this(provider, Clock.systemUTC());
    }

    PasswordResetServiceImpl(SupabaseClient provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    @Override
    public void request(String email, String remoteAddress) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        limit("ip:" + remoteAddress, 20);
        limit("email:" + DigestUtils.md5DigestAsHex(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 3);
        provider.recover(normalized);
    }

    private synchronized void limit(String key, int maximum) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 10000) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many password reset requests. Please wait 15 minutes before trying again.");
            }
            window = new Window(0, now.plus(Duration.ofMinutes(15)));
        }
        if (window.count() >= maximum) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many password reset requests. Please wait 15 minutes before trying again.");
        }
        windows.put(key, new Window(window.count() + 1, window.expiresAt()));
    }

    private record Window(int count, Instant expiresAt) {
    }
}