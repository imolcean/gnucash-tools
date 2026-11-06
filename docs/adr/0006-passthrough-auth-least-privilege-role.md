# Passthrough login to a least-privilege Postgres role; long-lived sessions

Status: Accepted, 2026-11-04

## Context

Single user, financial data, phone access over the home LAN and WireGuard only; never exposed to the internet. The user wants lightweight deployment (no Keycloak, no extra DB) via Docker Compose pasted into OMV, reproducible and idempotent. gnucash-web's `passthrough` auth model (login credentials go straight to the DB connection) worked well for them. Re-typing a password on the phone for every entry is the friction to avoid.

## Decision

- **Passthrough login:** the login form's username and password open the DB connection. A custom `AuthenticationProvider` succeeds iff a test connection succeeds. No user table, no stored secrets in the app.
- **Dedicated least-privilege role**, created by a one-shot, idempotent `db-init` container (`postgres:17.11`, inline `psql`, superuser credentials from env) that the app `depends_on` with `service_completed_successfully`. Grants: CONNECT, schema USAGE, SELECT on all tables; INSERT/UPDATE/DELETE on `transactions`, `splits`, `slots`; USAGE on `slots_id_seq`; DELETE on `gnclock`.
- **Sessions** live in server memory with a per-session connection pool (max 2), closed on logout or session destroy. The session cookie lasts 365 days (`server.servlet.session.cookie.max-age`), so a phone browser keeps it when it closes; a session unused for 90 days ends (`server.servlet.session.timeout`) and closes its pool. Re-login is needed after that, after a year, or after a redeploy or restart. CSRF stays on.
  (Changed 2026-11-06 from `timeout=-1` with a browser-session cookie: phones dropped the cookie on close, which forced a login each time and left the old session and its pool open until restart.)
- Network exposure: LAN + WireGuard only. No public reverse-proxy host.

## Consequences

- A stolen session cookie is valid until restart or 90 days of disuse; acceptable only because the app is not reachable from the internet.
- Even with the app compromised, the role cannot alter accounts, commodities, prices or schema.
- A session holds its login, not its connections: the pool keeps none idle for more than about a minute, and
  after a database restart the next request reconnects without a new login. While the book is down, requests
  fail after 5 s (`togo.db.connection-timeout`) with a "Cannot reach the book" banner. If the role's password
  changes, the session's next request logs it out.
- Desktop and backups keep using their own role; the app role is additive.
