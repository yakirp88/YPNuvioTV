# Changelog — Nuvio YP AI

Changes to this AI fork are recorded here. Upstream: NuvioTV 1.1.0-beta.8.

## [Unreleased]

### Fixed
- CI Android SDK setup requests platform-tools explicitly instead of the retired tools package.

## [0.3.0] — 2026-10-25

### Added
- GitHub source branch `yp/ai-intro`, automatic build checks and versioned prereleases on each push.
- Central version metadata, release checksums and source archives.
- Persistent signing through a repository secret; publishing fails closed when the key is missing.

### Changed
- Future debug builds use the YP version metadata and a monotonically increasing CI build number.

### Limitations
- Version 0.2 APK import has passed GitHub checksum verification and is published; the new-source build is being validated.
- Automatic AI submissions remain disabled pending calibration; audio detection is not implemented.

## [0.2.0] — 2026-10-25

### Fixed
- Configured the official public anonymous backend for startup QR pairing.
- Reopened QR onboarding once for signed-out users who skipped the unconfigured 0.1 build.

### Verified
- Universal APK signature and ZIP integrity; package `com.nuvio.tv.yp`, Android version code 1072.
- Anonymous pairing RPC returned a valid official Nuvio link. Phone approval and TV login still require device testing.

## [0.1.0] — 2026-10-25

### Added
- Opt-in subtitle evidence, personal encrypted Gemini key and optional sparse video-frame analysis.
- IntroDB lookup before analysis; only missing segment types are considered.
- Top-right “מאתר נקודות דילוג” indicator during subtitle detection; silent background media analysis.
- End-of-episode review with boundary thumbnails, time corrections and explicit submission.
- Selection between manual review and automatic submission preference; automatic publishing is currently gated off.

### Limitations
- No audio detection, confidence calibration or durable submission journal.
- Network failures do not authorize duplicate reports; uncertain submissions are not retried in the same session.
