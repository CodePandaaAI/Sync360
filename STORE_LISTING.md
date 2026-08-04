# Sync360 Store Disclosure

## Short Description

Share text and files directly between your Android and desktop devices over your private local network.

## Security Notice

Sync360 currently uses trusted-network mode. The receiver approves offers in the UI, but requests and file sockets are not authenticated and transferred content is not encrypted by Sync360.

Use Sync360 only on a private home network or personal hotspot controlled by you. Do not use it on public or shared networks such as cafes, hotels, airports, schools, or offices.

## Privacy Summary

- Direct local-network transfer; no transfer cloud.
- No account, ads, analytics, tracking, or telemetry.
- Offer decisions, transfer state, and shared text are temporary runtime state.
- Received files remain on the receiving device.

## Publishing Checklist

- Keep the maintainer contact in `PRIVACY.md` current.
- Publish `PRIVACY.md` at a public URL for store submission.
- Keep store data-safety answers consistent with shipped code and permissions.
- Revisit this disclosure before adding crash reporting, analytics, cloud services, or encrypted pairing.
