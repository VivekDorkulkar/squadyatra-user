package com.squadyatra.user.service;

import java.util.UUID;

public interface IdentitySyncService {
    void process(UUID userId);
}