package com.squadyatra.user.service;

import java.util.UUID;

import com.squadyatra.user.dto.Deactivation;
import com.squadyatra.user.dto.PageView;
import com.squadyatra.user.dto.ProfileUpdate;
import com.squadyatra.user.dto.StatusChange;
import com.squadyatra.user.dto.TypeChange;
import com.squadyatra.user.dto.UserSummary;
import com.squadyatra.user.dto.UserView;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.UserType;

public interface UserService {
    UserView me(UUID userId);

    UserView get(UUID userId, UUID id);

    PageView<UserSummary> list(UUID userId, AccountStatus status, UserType type, int page, int size);

    UserView update(UUID userId, ProfileUpdate request);

    UserView deactivate(UUID userId, Deactivation request);

    UserView status(UUID userId, UUID id, StatusChange request);

    UserView type(UUID userId, UUID id, TypeChange request);

    void retrySync(UUID userId, UUID id);
}