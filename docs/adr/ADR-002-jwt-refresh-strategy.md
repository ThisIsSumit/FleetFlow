# ADR: JWT + refresh token strategy

## Context
Stateless API auth with revocable sessions and no long-lived bearer secret in JavaScript.

## Decision
15-minute JWT in memory plus a rotating opaque refresh token in an HttpOnly SameSite=Strict cookie, stored hashed, with reuse detection that revokes all sessions.

## Alternatives
Server sessions (simple revocation, sticky/shared state); long-lived JWT (no revocation); refresh token in localStorage (XSS-exposed).

## Trade-offs
Revocation is bounded by the 15-minute access token. Concurrent refreshes look like reuse, so clients must serialise them. Cookie scope requires same-origin or a proxy.
