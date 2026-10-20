# SquadYatra User Service

Standalone Java 21 / Spring Boot 4 / Maven service for SquadYatra user profiles and account lifecycle. Default port: **8081**. Compatible with the existing gateway's `/api/users/**` route and the frontend's `consumer`, `agency`, and `admin` roles.

## Responsibilities

- Public, local-first registration with name, email, phone number, and password; map the resulting Supabase identity to the local record.
- Read and update a private profile, contact details, birth date, location, locale, time zone, and notification/marketing preferences.
- Self-deactivate; administratively activate, deactivate, or suspend accounts.
- Admin-only user-type changes and paginated, filtered user listing.
- Supabase-backed email/password login, registration, password-recovery requests, and password updates, without storing passwords or reset tokens.
- Database-backed ownership and admin checks, audit records, version-conflict protection, and durable Supabase identity synchronization.
- Flyway migrations, health/readiness probes, correlation IDs, validation, and Problem Details errors.

Supabase remains the identity provider. This service mediates email/password login, signup, recovery, and password updates through Supabase Auth; Supabase issues and refreshes sessions. Passwords exist only in request memory and HTTPS calls to Supabase: they are never persisted or logged. This service never reads or writes Supabase's `auth` tables directly. An agency user type does not approve an agency or authorize its listings; Catalog must enforce onboarding, approval, staff membership, and ownership separately.

The reference architecture originally places consumer profiles in Squad. This repository intentionally centralizes private account profiles as requested. Squad should reference these UUIDs and own departure-specific discovery consent, eligibility, parties, and membership. A birth date here is self-reported, not verified age evidence. No minimum-age policy is invented.

## Package Structure

```text
com.squadyatra.user
|-- client          Supabase HTTP integration
|-- config          Security and scheduling configuration
|-- controller      REST controllers
|-- dto             Individual request and response records
|-- entities        JPA entities with private fields and accessors
|-- enums           Account status and user type
|-- exceptions      Provider exception and HTTP error handlers
|-- filter          Request correlation filter
|-- properties      Configuration property bindings
|-- repository      Spring Data repositories
|-- scheduler       Scheduled synchronization trigger
|-- service         UserService, RegistrationService, PasswordResetService, IdentitySyncService interfaces
|   `-- imp         Corresponding service implementations
`-- UserApplication
```

Controllers and the scheduler depend on service interfaces. Business logic and transaction boundaries live in `service.imp`; repositories handle persistence. Tests mirror the packages they verify. Package reorganization does not change the API contract or database schema.

## Local Development

Requirements: JDK 21 or newer, internet access for Maven, Docker for local PostgreSQL, and an isolated Supabase development project. No external credentials are needed to run the automated tests.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\mvnw.cmd clean verify
```

On Linux/macOS use `sh ./mvnw clean verify`. Tests default to H2 in PostgreSQL compatibility mode. H2 does not replace PostgreSQL concurrency verification.

Copy `.env.example` to `.env`, fill in the local values, and keep `.env` uncommitted. Spring Boot imports that properties file; Docker Compose also reads it. In hosted environments, set the same variables through the platform's secret/configuration settings.

The bundled PostgreSQL 18 container publishes on `localhost:5433`. Set `DATABASE_URL=jdbc:postgresql://localhost:5433/squadyatra` in `.env` when using Compose. For Layerbase, use its pooled PostgreSQL endpoint with `sslmode=require` and supply the rotated password through the ignored `.env`; do not commit it.

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Or run the packaged artifact:

```powershell
java -jar target/squadyatra-user-0.0.1-SNAPSHOT.jar
```

Health: `http://localhost:8081/actuator/health`. Readiness: `/actuator/health/readiness` includes database connectivity. Database migrations must be valid for startup. JWTs are validated only by the gateway; route user-service requests through it so the service receives gateway-verified identity headers. Supabase outages do not block local profile operations; password recovery returns 503 and identity synchronization retries.

## Configuration

| Variable | Purpose |
| --- | --- |
| `PORT` | HTTP port; defaults to 8081, supports Render's assigned port |
| `DATABASE_URL` | JDBC PostgreSQL URL; use the provider's TLS parameters in hosted environments |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Dedicated database role and secret |
| `DATABASE_POOL_SIZE` | Maximum JDBC connections; defaults to 5 |
| `SUPABASE_URL` | Required Supabase project URL used for Auth API calls |
| `SUPABASE_PUBLISHABLE_KEY` | Required for public Auth signup and email/password login; use the project's publishable/anon key, not its service-role key |
| `SUPABASE_SERVICE_ROLE_KEY` | Server-only legacy service-role JWT for Auth admin sync, recovery, and password updates |
| `PASSWORD_RESET_REDIRECT` | Fixed frontend recovery URL from `.env`; allowlist it in Supabase Auth |
| `EMAIL_CONFIRMATION_REDIRECT` | Fixed frontend login URL from `.env` used by signup confirmation emails; allowlist it in Supabase Auth |

Use HTTPS outside isolated local development. Keep service-role credentials out of frontend/Vercel public configuration. This client expects the legacy service-role JWT accepted as a bearer token; do not substitute an opaque publishable/secret key without adapting and verifying the provider's current admin-auth contract.

The service owns its tables in the configured PostgreSQL database (`squadyatra`). Flyway and Hibernate use the selected database directly. Provision a restricted database role. In production, use separate Flyway migration credentials through `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` and grant the runtime role only required DML. Do not expose these tables through Supabase's browser-facing Data API. No cross-service foreign keys or auth-schema migrations are used.

## API

Use gateway URLs for all client requests and do not expose the user service directly. The gateway validates JWTs and forwards verified identity headers. The versioned OpenAPI contract is in `contracts/openapi/users-v1.yaml`.

| Method | Gateway Path | Authorization / Behavior |
| --- | --- | --- |
| POST | `/api/users` | Public registration; name, email, phoneNumber, and password required; no JWT |
| POST | `/api/users/login` | Public email/password login; returns Supabase session tokens |
| GET | `/api/users/oauth` | Public, validated redirect for Google OAuth initiation |
| POST | `/api/users/password-reset` | Public recovery email request; generic response, rate-limited |
| PUT | `/api/users/password-reset` | Valid recovery JWT required; gateway supplies verified identity; password must be 12–128 characters |
| GET | `/api/users/me` | `users:read`; active current user |
| PUT | `/api/users/me` | `users:write`; replace editable profile fields |
| POST | `/api/users/me/deactivate` | `users:write`; own non-admin account; reason and version required |
| GET | `/api/users/{uuid}` | `users:read`; owner or active database admin |
| GET | `/api/users?page=0&size=20&status=ACTIVE&type=CONSUMER` | `users:read`; active database admin, size 1–100 |
| PUT | `/api/users/{uuid}/status` | `users:write`; admin, not self; body `{ "status": "active", "reason": "Reviewed", "version": 1 }` |
| PUT | `/api/users/{uuid}/type` | `users:write`; admin, not self; body `{ "userType": "agency", "reason": "Approved onboarding", "version": 0 }` |
| POST | `/api/users/{uuid}/identity-sync` | `users:write`; admin; requeue failed provider synchronization |

JSON enums use lowercase. Query filters use uppercase Java enum names. Return the last received `version` with every profile/admin mutation; stale writes return 409. PUT profile requests replace **all** editable fields, so optional omissions clear those fields. The server controls IDs, user type, status, timestamps, and version. `id` is the stable local user UUID; `supabaseUserId` is the mapped Supabase Auth UUID/JWT `sub`. The gateway validates JWTs and forwards `X-User-Id`; the service maps that Supabase UUID to the local UUID, which may differ. User lists intentionally omit contact information and birth dates; full profiles are owner/admin-only. The following is a profile-update request, not a registration request:

```json
{
	"fullName": "Aarav Sharma",
	"phone": "+919876543210",
	"dateOfBirth": "1998-04-12",
	"city": "Pune",
	"countryCode": "IN",
	"timeZone": "Asia/Kolkata",
	"locale": "en-IN",
	"bio": "Enjoys hiking",
	"emailNotifications": true,
	"marketingConsent": false,
	"version": 0
}
```

Phone numbers use E.164, country codes use two uppercase letters, and time zones use IANA names. Dates of birth must be in the past. Marketing consent defaults to false and changes are timestamped. Notification preferences do not suppress essential security communications. Email is submitted during registration and matched against the provider's signup response; email confirmation is enforced by Supabase's configured policy. Email changes must go through Supabase and an operationally reviewed profile reconciliation until a verified identity-update webhook is added.

Errors use `application/problem+json` with a correlation ID; no request values, passwords, tokens, or provider bodies are logged. Expected statuses: 400 validation, 401 invalid credentials or missing/invalid gateway token, 403 permission/ownership/inactive account, 404 missing profile, 409 duplicate/stale update, 429 login/recovery throttle, and 503 provider unavailable. Audit reasons are private operator data: never include secrets or identity-document contents.

## Registration Flow

The new-user screen calls `POST /api/users` **without** an Authorization header:

```json
{
	"fullName": "Aarav Sharma",
	"email": "aarav@example.com",
	"phoneNumber": "+919876543210",
	"password": "<12-128 character password>"
}
```

If Supabase CAPTCHA protection is enabled, also supply `captchaToken` from the frontend CAPTCHA challenge. Do not send `userType`, roles, permissions, or a Supabase UUID; registration always creates a consumer.

1. Validate the request and check the normalized email in PostgreSQL. If a local record already exists, return 409 without contacting Supabase.
2. Outside the database transaction, call Supabase `/auth/v1/signup` with the email/password, `full_name` / `phone_number` metadata, and the fixed `EMAIL_CONFIRMATION_REDIRECT` URL. Supabase redirects confirmation links to the frontend login page; add that exact URL to Supabase Auth's redirect allowlist. Supabase rejects an existing identity; an obfuscated response with no identities is never mapped. Signup does not require or inspect session tokens. The contact phone is not treated as a verified Supabase phone/SMS identity.
3. Only after Supabase confirms a new identity, save the completed, active PostgreSQL account with its own UUID and the returned Supabase UUID, audit the change, and queue trusted role synchronization. A second PostgreSQL duplicate check and the unique email constraint protect concurrent requests.
4. Return 201 with `id`, `supabaseUserId`, and a message. No password, session, refresh token, or access token is returned. Email confirmation is required before login.
5. After Supabase confirms the email, the frontend calls `POST /api/users/login` through the gateway. The user service validates the password with Supabase and issues tokens only when the returned Supabase identity matches a completed, active PostgreSQL account. The browser stores the tokens with Supabase `setSession`, verifies the persisted session, and loads `GET /api/users/me` through the gateway before granting access.

The database has no password or password-hash column. Request DTO diagnostic strings redact credentials and JSON serialization excludes password/CAPTCHA values. Require HTTPS, disable request-body logging in every proxy/APM layer, and configure Supabase Site URL, email templates, password policy, delivery, and signup CAPTCHA before launch.

Database and provider changes are not one distributed transaction. If Supabase creates an identity but the subsequent PostgreSQL transaction fails, the provider identity may require operator reconciliation; a later signup will be rejected by Supabase rather than mapped automatically. Existing PostgreSQL records are always rejected as duplicates. Never accept a caller-supplied UUID or automatically attach an existing provider account just because emails match.

Monitor registration audit events and reconcile provider identities created without a corresponding PostgreSQL record using an authorized operator workflow. Registration is bounded to ten requests per TCP peer per fifteen minutes in this process, with distributed gateway/WAF limits still required.

Flyway V2 adds the unique nullable mapping and registration state, and backfills existing accounts with `supabase_user_id=id` and `COMPLETED`. Existing local primary keys and cross-service references are not rewritten. Apply migrations before deploying the new service version.

## Frontend and Gateway Integration

1. The signup screen calls the public backend registration endpoint described above. Do not call Supabase signup separately: the backend checks PostgreSQL first, then creates the Supabase identity, and saves the PostgreSQL record only after Supabase reports a new identity.
2. Configure a **server-managed** Supabase custom access-token hook or trusted app metadata to supply `permissions: ["users:read", "users:write"]` to eligible users before protected profile requests. Do not source permissions from `user_metadata`, and do not grant admin by default. Signup itself needs no permissions or JWT.
3. Supabase email confirmation is required before sign-in. `POST /api/users/login` validates the credentials and requires a matching active PostgreSQL account; then the frontend persists and verifies the Supabase session and loads `GET /api/users/me` through the gateway. Do not call registration again as a profile-bootstrap step after login.
4. Keep `USERS_SERVICE_URL=http://localhost:8081` in the gateway locally; configure its private deployed URL on Render. The existing StripPrefix rule already matches this service.
5. Keep the **specific auth routes before generic users rules** in gateway configuration. Registration, login, and recovery initiation are public; password-reset completion requires authentication without an extra permission:

```yaml
squadyatra:
	gateway:
		access-rules:
			- path: /api/users
				methods: [POST]
				public-access: true
			- path: /api/users/password-reset
				methods: [POST]
				public-access: true
			- path: /api/users/login
				methods: [POST]
				public-access: true
			- path: /api/users/oauth
				methods: [GET]
				public-access: true
			- path: /api/users/password-reset
				methods: [PUT]
			- path: /api/users/**
				methods: [GET]
				permissions: [users:read]
			- path: /api/users/**
				permissions: [users:write]
```

Retain the gateway's other existing rules. These gateway rules are required so logged-out users can register, sign in, and request recovery while all other user operations remain protected.

For this service's recovery API, send `{ "email": "traveler@example.com" }`. It returns a generic 202 for provider eligibility/client errors and never looks up local account existence. The fixed redirect prevents client-supplied open redirects. The frontend recovery page handles Supabase's recovery session/event and submits the new password with `PUT /api/users/password-reset`; the gateway validates the recovery JWT before the service updates that verified Supabase identity. Configure Supabase email delivery, recovery redirect allowlist, password policy, and CAPTCHA before launch.

Recovery has bounded per-process fixed-window limits: three requests per normalized email and twenty per TCP peer per fifteen minutes. It does not trust spoofable forwarding headers. Behind the gateway, the peer may be shared, making that limit conservative. Deploy gateway/WAF-level distributed limits and configure trusted proxy handling deliberately before horizontal scaling. The existing gateway does not yet provide CORS/rate limits; those remain integration requirements, not features added here.

## Administration and Synchronization

First administrator bootstrap is an explicit deployment operation, not public signup. Register the operator through the public endpoint, complete email verification, verify both its local ID and mapped Supabase identity out of band, and execute this with an authorized database operator account, substituting the verified **local** UUID:

```sql
START TRANSACTION;
UPDATE users
SET user_type = 'ADMIN', updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000000000') AND status = 'ACTIVE';
INSERT INTO identity_sync(user_id, attempts, next_attempt_at, failed)
SELECT id, 0, CURRENT_TIMESTAMP(6), FALSE FROM users
WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000000000') AND user_type = 'ADMIN'
ON DUPLICATE KEY UPDATE attempts = 0, next_attempt_at = CURRENT_TIMESTAMP(6), failed = FALSE;
INSERT INTO user_audit(id, actor_id, user_id, action, reason, correlation_id, occurred_at)
SELECT UUID_TO_BIN(UUID()), id, id, 'ADMIN_BOOTSTRAPPED', 'Approved operator bootstrap', 'deployment-bootstrap', CURRENT_TIMESTAMP(6)
FROM users WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-000000000000') AND user_type = 'ADMIN';
COMMIT;
```

Verify exactly one intended account changed and record the deployment approval. Refresh its Supabase session after synchronization. Admin self-deactivation/demotion is blocked; maintain at least two approved operator accounts and an audited break-glass procedure.

Account changes and audit records commit atomically with coalesced `identity_sync` work. The worker reads the latest account state under a per-user database lock, updates Supabase's trusted `app_metadata.role` / `roles`, preserves unrelated metadata/permissions, and bans/unbans provider login. Only this service should write these application-role fields. Custom hooks must derive top-level `user_role` from this canonical role rather than retain a conflicting role. Existing unrelated permission sets are not automatically granted or revoked; manage them through the trusted permission policy.

The worker retries up to eight times with bounded exponential backoff; exhausted work remains marked failed. Monitor `SELECT * FROM identity_sync WHERE failed = TRUE` and queue age/attempt counts. Correct provider configuration, then use the admin identity-sync endpoint to retry. Repeated state synchronization is idempotent. Database locks span at most the bounded provider HTTP calls (2-second connect / 5-second read each); account updates may briefly wait behind a synchronization attempt.

Local authorization rejects inactive users and uses current database roles immediately, even with an old JWT. **Supabase bans and role metadata changes do not revoke already-issued access tokens in the gateway or other services.** Other services must perform authoritative status/permission checks for sensitive operations or implement a revocation/version policy. Use short token lifetimes and session refresh; do not claim global instant revocation. This queue is provider reconciliation, not a RabbitMQ domain-event outbox. Cross-service user lifecycle events remain an explicit follow-up contract.

## Deployment and Verification

Build with `docker build -t squadyatra-user .` and deploy the Dockerfile to Render as a private service when available. Set all runtime environment variables, configure `/actuator/health` as the health check, restrict direct access, enforce HTTPS at the edge, and configure backups and restore drills. Size connection pools across all services. No CORS policy is enabled in this backend: browsers should use the gateway.

`clean verify` runs unit, migrated-database HTTP/security, and local mock-provider contract tests and creates the executable JAR. Tests verify a committed local row exists before the signup HTTP call and check UUID mapping and failure states. CI uses a disposable PostgreSQL 18 database through `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, and `TEST_DATABASE_PASSWORD`; never point tests at production. Live Supabase signup/confirmation, recovery delivery, signing-key rotation, cross-service revocation, and deployment networking still need staging verification. Identity document storage, discovery profiles, age verification, deletion/retention policy, notification event delivery, and agency approval remain with their owning workflows rather than being fabricated here.