# Integrations: email, address search, map tiles

## Email
`MAIL_PROVIDER=smtp` sends multipart (text + branded HTML) mail through any SMTP server (`MAIL_HOST/PORT/USER/PASSWORD/AUTH/STARTTLS`, `MAIL_FROM`). Docker Compose defaults to Mailpit, a local SMTP catcher with a web inbox at http://localhost:8025. `MAIL_PROVIDER=mock` (default outside compose) only logs the message, including links, and must not be used in production. Delivery failures are logged (recipient domain only) and swallowed so signup and reset never fail or reveal account existence because of email. Not built: bounce handling, DKIM/SPF setup (do this at your provider), delivery queue with retries.

## Address search (geocoding)
`GET /api/v1/geocode?q=` proxies a Nominatim-compatible API (`GEOCODER_URL`), restricted by `GEOCODER_COUNTRIES` (default `in`), cached 24 h in Redis, limited to 30 searches/min/user and `GEOCODER_MAX_PER_SECOND` upstream. The order form offers type-ahead search plus the old presets and manual coordinates as fallbacks. The public nominatim.openstreetmap.org is for light development use only: set `GEOCODER_USER_AGENT` with a real contact and use your own instance or a commercial provider for production.

## Map tiles
Leaflet reads `VITE_TILE_URL` and `VITE_TILE_ATTRIBUTION` (see `frontend/.env.example`). The default is the public OpenStreetMap tile server, which has a usage policy that does not allow heavy production traffic. Set a tile provider or your own tile server before real use.
