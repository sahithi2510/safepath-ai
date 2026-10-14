# SafePath AI — Security Design

> **Last Updated:** 2026-10-08

---

## 1. Authentication

### JWT (JSON Web Token)

- Algorithm: HS256 (HMAC-SHA256)
- Secret: minimum 256-bit random string from environment variable
- Expiry: 24 hours (configurable via `JWT_EXPIRATION_MS`)
- Claims: `sub` (user ID), `email`, `roles`, `iat`, `exp`

**Token lifecycle:**
1. User logs in → Spring Boot generates JWT
2. Mobile stores JWT in **Expo SecureStore** (encrypted iOS Keychain / Android Keystore backed)
3. Mobile sends `Authorization: Bearer <token>` on every authenticated request
4. Spring Boot JwtFilter validates token on every request
5. On expiry, user must re-authenticate

### Password Security

- Algorithm: **BCrypt** with strength factor 12
- Never store plaintext passwords
- Never log passwords

---

## 2. API Security

### Input Validation

All API inputs use **Bean Validation** (`@Valid`, `@NotNull`, `@Size`, etc.):
- Email format validation
- Password strength requirements (min length, character types)
- Coordinate range validation (latitude −90–90, longitude −180–180)
- Field length limits to prevent excessively large inputs

### Global Exception Handling

`GlobalExceptionHandler` (Spring `@ControllerAdvice`) catches all exceptions and returns:
- Structured JSON error responses
- HTTP status codes
- NO stack traces in responses
- NO internal system information

### CORS

Explicit CORS configuration:
```java
allowedOrigins = from CORS_ALLOWED_ORIGINS environment variable
allowedMethods = GET, POST, PUT, DELETE, OPTIONS
allowedHeaders = Authorization, Content-Type
allowCredentials = false
```

### Rate Limiting & Abuse Prevention

- **Community Report Submissions (`POST /api/reports`):**
  - **Implemented in `ReportRateLimiterService`:** Thread-safe sliding-window rate limiter enforcing a maximum of **5 report submissions per 10 minutes** per authenticated user UUID.
  - Exceeding the threshold triggers HTTP `429 Too Many Requests` with retry-after header calculation.
  - Prevents automated spam, denial-of-service flooding, and false hazard report campaigns without requiring external caching layers.
- **Authentication endpoints:** Max 10 requests/minute per IP (future edge proxy / gateway tier).
- **Route search:** Max 60 requests/minute per user.

### Privacy & PII Protection
- **Reporter Identity Isolation:** In `CommunityReportService`, public queries (`GET /api/reports/nearby`) return only verified reports and strictly omit `user_id`, email, full name, or phone numbers.
- **Location Consent:** Incident coordinates are optional and require explicit confirmation (`locationConfirmed: true`). If withheld, the record is stored with `UNKNOWN` spatial precision.
- **Untrusted User Content:** Report descriptions are strictly capped at 1000 characters and stripped of HTML/script elements prior to persistence.

---

## 3. Authorization

### Roles

| Role | Permissions |
|------|------------|
| `USER` | Submit reports, search routes, manage own emergency contacts |
| `MODERATOR` | Verify/reject community reports |
| `ADMIN` | All permissions |

### Method-Level Security

Spring Security `@PreAuthorize` on service methods:
```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
@PreAuthorize("#userId == authentication.principal.id")
```

---

## 4. Secrets Management

| Secret | Storage |
|--------|---------|
| JWT secret | Environment variable `JWT_SECRET` |
| Database password | Environment variable `POSTGRES_PASSWORD` |
| All credentials | Environment variables only |

Rules:
- Never hardcode secrets in source code
- Never commit `.env` to Git
- `.env.example` contains only placeholder values
- Production: use proper secret management (e.g., environment variables injected by deployment system)

---

## 5. Mobile Security

| Concern | Solution |
|---------|---------|
| JWT storage | Expo SecureStore (never AsyncStorage) |
| Network requests | HTTPS only in production |
| Sensitive display | No sensitive data in logs |
| Certificate pinning | Documented as future improvement |

---

## 6. Database Security

- Spring Boot connects with least-privilege user (`safepath_user`)
- `safepath_user` has access only to `safepath_db`
- No direct mobile access to database
- Parameterized queries via JPA (SQL injection prevention)

---

## 7. Community Report Security

- All reports must come from authenticated users
- User ID is taken from JWT, not from the request body
- A single user cannot make more than N reports per time window (to be implemented)
- Unverified reports have no direct effect on safety scores

---

## 8. Audit Logging

Security-relevant events are logged to `audit_logs`:
- User registration
- Login success/failure
- Password changes
- Report submission
- Report verification
- SOS events
- Admin actions

---

## 9. FastAPI ML Service Security

- Not exposed to the internet; only accessible from Spring Boot (internal network)
- In Docker Compose: ML service port not exposed to host
- No authentication required (internal service)
- Input validation via Pydantic schemas

---

## 10. Production Checklist

- [ ] HTTPS configured (TLS certificate)
- [ ] JWT secret is a strong random value (not default)
- [ ] Database password is strong (not default)
- [ ] CORS allows only known origins
- [ ] ML service not exposed publicly
- [ ] All logs reviewed for sensitive data
- [ ] Secrets not in Git history

---

*SafePath AI — security.md — 2026-10-08*
