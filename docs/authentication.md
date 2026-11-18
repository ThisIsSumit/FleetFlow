# Authentication

- **Access token:** JWT (HS256), 15 minutes, claims `sub`, `org`, `role`, `sid`. Held in browser memory only.
- **Refresh token:** 256-bit random value. Only its SHA-256 is stored (`refresh_sessions.token_hash`). Sent as an `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/v1/auth`.
- **Rotation + reuse detection:** every refresh replaces the token and remembers the previous hash. Presenting an already-rotated token revokes all of that user's sessions, except within `fleetflow.refresh.reuse-grace-seconds` (30 s) of the rotation: that is a concurrent refresh (two tabs reloading together), and the session is simply rotated again. The session row is locked during refresh, so simultaneous refreshes serialise instead of overwriting each other. The frontend also serialises refreshes within a tab (single-flight) and across tabs (Web Locks API). Trade-off: a stolen token replayed inside the 30 s window is not detected.
- **Passwords:** BCrypt cost 12. Login does equal work for unknown emails.
- **Password reset / email verification / invitations:** single-use tokens in `auth_tokens`, stored hashed, consumed by an atomic `UPDATE ... RETURNING`. Reset ignores unknown emails (always 204) and signs out every device. Email goes through `MailService`; the dev `MockMailService` logs links, so never use it in production.
- **Sessions:** `GET/DELETE /auth/sessions` per user; `logout-all` deletes them all.
- **Immediate revocation:** logout, session revoke, logout-all, role change, disabling, removal, password reset and detected theft delete the refresh session(s) *and* put their session ids on a Redis denylist (`revoked:sid:*`, TTL = access-token lifetime). `JwtAuthFilter` rejects access tokens of denylisted sessions, so they stop working at once.
- **WebSockets:** authenticated at handshake (token + denylist). A socket is closed (code 4001) when its access token expires or its session is revoked; the client reconnects with a fresh token.
- **Rate limits:** per IP for login, password reset, signup, verification/invitation links and refresh; per account for login (20 / 15 min); per user for resend-verification. Behind a proxy set `FORWARD_HEADERS_STRATEGY` so the IP is the client's.
- **Known limits:** if Redis is unavailable the denylist fails open and access tokens live out their 15 minutes.
