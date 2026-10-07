# Authentication

- **Access token:** JWT (HS256), 15 minutes, claims `sub`, `org`, `role`, `sid`. Held in browser memory only.
- **Refresh token:** 256-bit random value. Only its SHA-256 is stored (`refresh_sessions.token_hash`). Sent as an `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/v1/auth`.
- **Rotation + reuse detection:** every refresh replaces the token and remembers the previous hash. Presenting an already-rotated token revokes all of that user's sessions. Consequence: concurrent refreshes with one token look like theft, so the frontend serialises refreshes (single-flight).
- **Passwords:** BCrypt cost 12. Login does equal work for unknown emails.
- **Password reset / email verification / invitations:** single-use tokens in `auth_tokens`, stored hashed, consumed by an atomic `UPDATE ... RETURNING`. Reset ignores unknown emails (always 204) and signs out every device. Email goes through `MailService`; the dev `MockMailService` logs links, so never use it in production.
- **Sessions:** `GET/DELETE /auth/sessions` per user; `logout-all` deletes them all.
- **Known limits:** a disabled or role-changed user keeps a valid access token for up to 15 minutes (stateless JWT). Role changes and disabling revoke refresh sessions so the window is bounded. WebSockets are authenticated at handshake only.
