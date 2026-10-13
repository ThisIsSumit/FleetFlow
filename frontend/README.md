# FleetFlow frontend
React 18 + Vite + TypeScript + Tailwind, TanStack Query for server state, Zustand for auth/theme/toasts only.

    npm install
    npm run dev        # http://localhost:5173, proxies /api to http://localhost:8080

The dev proxy keeps the app same-origin so the HttpOnly `SameSite=Strict` refresh cookie works.
Auth: access token in memory only; on load the app calls `/auth/refresh` to restore the session; a 401 triggers one shared refresh (single-flight) and a retry.
Routes marked "Soon" render an explicit not-built page instead of fake data.
