# Changelog — Nuvio YP AI

Changes to this AI fork are recorded here. Upstream: NuvioTV 1.1.0-beta.8.

## [Unreleased]

## [0.3.3] — 2026-10-26

### Fixed
- Natural playback completion no longer cancels an active evidence collector or rejects late review proposals.
- Reserve the end-of-episode review before remote analysis, preventing automatic episode navigation from winning the race. Closing the summary still releases navigation.
- Offer subtitle proposals before optional native frame extraction, which may be slow or unsupported.
- Preserve numeric Gemini HTTP failures instead of flattening them into an uninformative IOException; preserve successful subtitle proposals when visual analysis fails.

### Added
- Bounded chronological detection log with fixed action names, elapsed times, numeric evidence counts, settings flags and sanitized HTTP outcomes.
- Manual upload of the latest detection log to dpaste.org from diagnostics settings. Returns a copyable URL; requests 7-day expiry and discloses link access. No background uploads, credentials, response bodies or media/subtitle URLs.
- End-of-episode status summary when review mode has no proposals, including in-progress, existing reports, missing metadata, lookup failures, disabled cloud and unsuccessful analysis.
- Local sanitized detection status in screenshot/copy diagnostics. No API response bodies, keys or media URLs are included.

### Verification
- Device root cause is not yet confirmed. The completion race and silent-error paths were found by code inspection; user playback testing remains necessary.


## [0.3.2] — 2026-10-26

### Fixed
- Escaped the literal closing brace in the subtitle markup-removal regex. The original pattern is accepted by desktop Java but rejected by Android's ICU backend, causing an ExceptionInInitializerError on first local analysis.
- Reproduced the old-pattern rejection with ICU4C and added a CI compatibility check of all production regex constants and markup-removal behavior.

### Added
- Local crash diagnostics after restart and in YP intro-detection settings: paginated TV screenshots and manual copy of the complete sanitized report.
- Capture Java exception classes and bounded stack frames while preserving the existing system crash handler. Error messages, raw logcat, account/device identifiers, URLs and API keys are omitted.
- Android 11+ previous-process reason/status and memory summary for native crashes, ANRs and low-memory exits when the OS provides it. Raw OS descriptions and trace streams are never imported.
- Record the intro-detection stage and safely handled background failures for diagnosis without a full app crash.
- Privacy, bounds and pagination regression tests.

### Changed
- Detection ring now appears at the absolute top-left, including Hebrew layouts.
- Superseded build runs are cancelled so the latest fix can be checked without waiting for obsolete builds.

### Verification
- GitHub Actions run 40248644384 passed the Android build and selected unit suites for skip visibility, failure isolation and diagnostic privacy/pagination.
- Passed 61 production analyzer/review checks and the native ICU compilation/markup regression.
- Universal full-debug APK uses version code 500008 and the original 0.2 signing certificate; package/version, signature and ZIP CRC verified before release import.
- Device playback has not yet been tested with this update.

### Limitations
- Fatal reports require the new version to be installed before reproducing; Android may supply limited prior-version exit information on an in-place update.
- Full disk, forced termination and native crashes may prevent a Java report. No report is uploaded automatically.
- Direct CI publication uses the signing secret; private local signing with the original key and verified release import provides an alternative without web sign-in.

## [0.3.1] — 2026-10-26

### Fixed
- Render the passive detection indicator inside the existing player window rather than creating a separate Android Popup window. Subtitle-only collection reproduces the user report, ruling out a dependency on cloud analysis. The indicator is a candidate, not a proven root cause.
- Contain exceptions from optional intro-detection settings/preflight reads and its collector, outside the existing per-analysis handler, so they do not terminate the playback coroutine scope.
- Clear pending AI review and show failed detection state on an optional-session failure. Preserve coroutine cancellation when the player closes.

### Verification
- Added regression checks for upstream failures, collector failures and cancellation propagation.
- This is failure isolation, not a confirmed diagnosis of the reported device crash. A device log and playback test are still needed; native decoder/process crashes are not caught by this boundary.
- This version was source-only; its changes are included in the 0.3.2 signed update.

### Added
- Windows helper for private signing-secret configuration and prominent GitHub APK links.

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
- Version 0.2 APK import passed GitHub checksum verification and is published.
- GitHub Actions built the 0.3 source successfully and passed 60 analyzer/review checks.
- Future APK publication awaits YP_DEBUG_KEYSTORE_BASE64; no disposable-key APK was distributed.
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
