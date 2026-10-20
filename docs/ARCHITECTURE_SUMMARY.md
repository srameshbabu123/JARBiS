# JARBiS Architecture Summary

## Dockerization

- **Multi-container orchestration** via Docker Compose: backend, Postgres, Kafka, and Kafka-init with health checks and dependency ordering
- **Multi-stage builds** reduce backend image from 500MB → 150MB; Maven builder excluded from runtime
- **Currently dockerized 4 entities**: Spring backend, PostgreSQL 15, Apache Kafka 3.9.1, and Kafka topic initialization
- **Single docker-compose.yml** for local development; all services in one network

**Talking Point:** One `docker-compose up` spins up the entire stack locally; foundation for scaling to production.

---

## Dockerization: Future

- **Two frontend containers** (React/Vue) alongside backend; shared NGINX reverse proxy on port 80
- **Image registry** (DockerHub/ECR) with automated builds on Git push; tag by commit hash for traceability
- **Environment parity via Compose configs**: separate `.env` files for dev/staging/prod swap database URLs and secrets without code changes
- **Auto-healing** with restart policies and health checks; containers self-recover on failure

**Talking Point:** Environment-specific configs eliminate drift between local and production; NGINX reverse proxy simplifies frontend routing.

---

## Authentication: Current State

- **Single JWT token** issued by Spring; no session DB or Redis dependency
- **Local token validation** in `JwtAuthenticationFilter`; no network calls on every request
- **Bcrypt-hashed passwords** (Spring Security defaults); salted to prevent rainbow tables
- **Public endpoints** (`/auth/register`, `/auth/login`) and protected endpoints (require Bearer token)

**Talking Point:** Zero latency auth checks = fast response times and horizontal scalability without sticky sessions.

---

## Authentication: Future Plans

- **Refresh token rotation** (7-day refresh + 1-hour access token); revokable for forced logout across all sessions
- **Multi-factor auth (TOTP)** for high-value accounts; integrates with Google Authenticator, no SMS costs
- **OAuth2/OIDC provider** mode for mobile app, partner portal, and ecosystem single sign-on
- **Audit log trail** (login, register, failed attempts, MFA events) for compliance (SOX, GDPR) and intrusion detection

**Talking Point:** Refresh tokens + MFA harden security while OIDC enables partnership integrations; audit logs satisfy regulatory requirements.
