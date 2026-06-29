# Roadmap

Sync360 is early. This roadmap is honest about what exists and what is still planned.

## Current milestone

Android local request/response proof:

- Android NSD advertisement and discovery.
- Dynamic Ktor server port advertisement.
- Nearby device route resolution.
- Ktor client request to another device.
- Receiver Accept/Decline proof.

## MVP

The first useful MVP should support:

- Android-to-Android nearby device discovery.
- Receiver approval for incoming send requests.
- Sending a direct text snippet.
- Sending one file.
- Basic progress state.
- Basic success/failure result.
- Manual reload/rescan.

## Near-term work

### Networking

- Replace ping-based experiment with real send-offer route.
- Add request DTOs for send offers.
- Add response DTOs for accepted/declined/busy/error.
- Add timeout for receiver decisions.
- Prefer IPv4 host address where available.
- Handle IPv6 URL formatting correctly.
- Improve discovery lifecycle and advertisement cleanup.

### Send UI

- Add file picker.
- Add selected file list.
- Add direct text input.
- Allow removing selected items.
- Keep selected items after sending until explicitly cleared.
- Show outgoing request state.
- Show send result or failure.

### Receive UI

- Show incoming sender name/device.
- Show incoming item summary.
- Accept/Decline real send offers.
- Show receive progress.
- Show received result.
- Handle busy receiver state.

### Transfer

- Send one text object.
- Send one file.
- Save received files safely on Android.
- Add progress updates.
- Validate file size and received byte count.

## Future work

- Multiple files in one send bundle.
- Mixed text and file bundles.
- Desktop support.
- iOS investigation.
- Transfer history, if it fits the product.
- Clipboard-oriented flows, if they stay explicit and privacy-friendly.
- Better onboarding and empty states.
- Better local-network troubleshooting UI.

## Security phase

Security should be designed after the simple local flow is working and understood.

Possible security work:

- sender identity validation
- session token exchange
- request signing
- nonce/timestamp replay protection
- transfer token validation
- file name and path validation
- transfer size limits
- integrity checks
- encryption evaluation

## Not planned right now

- Cloud sync.
- Accounts.
- Permanent chat history.
- Database-backed device ledger.
- WebRTC or NAT traversal.
- Background clipboard scraping.
