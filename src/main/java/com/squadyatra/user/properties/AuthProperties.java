package com.squadyatra.user.properties;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("supabase.auth")
public record AuthProperties(URI url, String serviceRoleKey, URI passwordResetRedirect, String publishableKey) {
    public String issuer() {
        if (url == null || !url.isAbsolute() || url.getHost() == null
                || !("https".equals(url.getScheme()) || "http".equals(url.getScheme()))) {
            throw new IllegalStateException("SUPABASE_URL must be an absolute HTTP(S) project URL");
        }
        return url.toString().replaceAll("/+$", "") + "/auth/v1";
    }
}