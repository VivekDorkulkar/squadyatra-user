package com.squadyatra.user.controller;

import java.net.URI;
import java.util.UUID;

import com.squadyatra.user.dto.Accepted;
import com.squadyatra.user.dto.Deactivation;
import com.squadyatra.user.dto.LoginRequest;
import com.squadyatra.user.dto.LoginResult;
import com.squadyatra.user.dto.PageView;
import com.squadyatra.user.dto.PasswordReset;
import com.squadyatra.user.dto.PasswordUpdate;
import com.squadyatra.user.dto.ProfileUpdate;
import com.squadyatra.user.dto.Registration;
import com.squadyatra.user.dto.RegistrationResult;
import com.squadyatra.user.dto.StatusChange;
import com.squadyatra.user.dto.TypeChange;
import com.squadyatra.user.dto.UserSummary;
import com.squadyatra.user.dto.UserView;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.UserType;
import com.squadyatra.user.service.PasswordResetService;
import com.squadyatra.user.service.RegistrationService;
import com.squadyatra.user.service.UserService;
import com.squadyatra.user.service.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;
    private final PasswordResetService passwords;
    private final RegistrationService registrations;
    private final UserAuthService authentication;

    public UserController(UserService users, PasswordResetService passwords, RegistrationService registrations,
                          UserAuthService authentication) {
        this.users = users;
        this.passwords = passwords;
        this.registrations = registrations;
        this.authentication = authentication;
    }

    @PostMapping
    public ResponseEntity<RegistrationResult> register(@Valid @RequestBody Registration request,
                                                      HttpServletRequest servletRequest) {
        RegistrationResult result = registrations.register(request, servletRequest.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/users/" + result.id())).body(result);
    }

    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authentication.signIn(request, servletRequest.getRemoteAddr());
    }

    @GetMapping("/oauth")
    public ResponseEntity<Void> oauth(@RequestParam("url") URI authorizationUrl) {
        return ResponseEntity.status(HttpStatus.FOUND).location(authentication.oauthRedirect(authorizationUrl)).build();
    }

    @GetMapping("/me")
    public UserView me(@RequestHeader("X-User-Id") UUID userId) {
        return users.me(userId);
    }

    @PutMapping("/me")
    public UserView update(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody ProfileUpdate request) {
        return users.update(userId, request);
    }

    @PostMapping("/me/deactivate")
    public UserView deactivate(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody Deactivation request) {
        return users.deactivate(userId, request);
    }

    @GetMapping("/{id}")
    public UserView get(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        return users.get(userId, id);
    }

    @GetMapping
    public PageView<UserSummary> list(@RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) UserType type,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return users.list(userId, status, type, page, size);
    }

    @PutMapping("/{id}/status")
    public UserView status(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id,
                           @Valid @RequestBody StatusChange request) {
        return users.status(userId, id, request);
    }

    @PutMapping("/{id}/type")
    public UserView type(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id,
                         @Valid @RequestBody TypeChange request) {
        return users.type(userId, id, request);
    }

    @PostMapping("/{id}/identity-sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Accepted retrySync(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        users.retrySync(userId, id);
        return new Accepted("Identity synchronization queued");
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Accepted reset(@Valid @RequestBody PasswordReset request, HttpServletRequest servletRequest) {
        passwords.request(request.email(), servletRequest.getRemoteAddr());
        return new Accepted("If the account is eligible, password recovery instructions will be sent");
    }

    @PutMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody PasswordUpdate request) {
        authentication.updatePassword(userId, request.password());
    }
}