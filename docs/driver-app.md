# Driver app

A mobile-first web app at `/driver` for users with the DRIVER role (they are redirected there after login; staff are redirected away).

- **Linking:** a DRIVER user is tied to one driver profile. Invite them from Settings > Team, choose role Driver and select the profile; accepting the invitation links the account. Unlinked driver accounts see a clear message instead of data.
- **What drivers can do:** set status (Available / On break / Offline; blocked while they have an active delivery, BUSY is system-managed), see assigned deliveries with customer, addresses and OSM directions links, move their own order through Arrived -> In transit -> Delivered or Failed (never Cancelled), and share location.
- **Location:** browser GPS (needs HTTPS except on localhost) or a built-in simulator that walks towards the current pickup/drop, sent over the WebSocket with REST fallback. Dispatch only considers drivers seen in the last 2 minutes.
- **Security:** the driver is always derived from the token; other drivers' orders return 404; drivers have no access to staff endpoints. Covered by `AccountFlowIT.driverPortalEnforcesOwnershipAndAllowedStatuses`.
- **Demo:** with `SEED_DEMO=true`, `driver@fleetflow.dev` (same password as the other demo users) is linked to the first seeded driver, who has an in-flight order.
- **Not built:** push notifications, offline mode, a native app, proof-of-delivery photos or signatures.
