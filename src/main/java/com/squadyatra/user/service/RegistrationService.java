package com.squadyatra.user.service;

import com.squadyatra.user.dto.Registration;
import com.squadyatra.user.dto.RegistrationResult;

public interface RegistrationService {
    RegistrationResult register(Registration request, String remoteAddress);
}