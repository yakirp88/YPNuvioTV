# Changelog — Nuvio YP AI

Changes to this AI fork are recorded here. Upstream: NuvioTV 1.1.0-beta.8.

## [Unreleased]

## [0.3.8] — 2026-10-27

### Fixed
- Device log ijHDF found 16 independent subtitle options but only examined eight, rejected seven and chose the viewing caption with zero descriptive cues. Preserve independent-provider label/SDH flags, inspect up to 24 independent and eight installed options with language diversity, and remove the English preference. Discovery does not use viewing-language or installed-addon filters.
- When embedded indexes are unavailable, use already timed embedded text or the explicitly selected/delayed viewing caption as additional timestamp references. SDH candidates still require strong alignment before use; unknown timing is never silently treated as correct.
- Download up to three candidates concurrently within the existing bounded acquisition pass; report each candidate's cue/description counts and timing outcome, plus total discoveries/languages/checks. No subtitle text, language labels, IDs or URLs enter the log.
- Cloud instructions explicitly accept descriptive captions in any language and distinguish named theme/opening clues from ordinary background music.

### Verification
- Targeted regression tests and Android build pending. The supplied log completed visual cloud analysis with zero proposals after an earlier text timeout; it does not demonstrate that all cloud requests lack connectivity.
- Provider metadata and cross-language timing vary. No bitmap OCR, unverified-timing submissions, audio analysis or automatic AI reporting is implemented.

## [0.3.7] — 2026-10-26

### Distribution
- Published signed version code 500022 through successful workflow 40409107403. Public APK full download matched SHA-256 02daf1b152d86320c41d04c2185e0ff2a4cd188136b27b7a20c21b3ea467c805 (156029800 bytes).
- Downloader code 8875821 was verified against the immutable GitHub 0.3.7 APK URL. Release anchor e9715f9bcae27f447700020d551dcf2ebaf1bd4f has identical non-workflow source to tested commit a9864e4bf2c624054a3428e78c77a43407e84a33.

### Changed
- Detection subtitle acquisition is independent of the displayed subtitle. Changing the viewing subtitle/delay no longer cancels in-flight detection (as happened in device log jVXUZ).
- Indexed Matroska text extraction reads subtitle blocks directly from Cues/cluster offsets without decoding video/audio, checks track/timestamp and uses bounded small HTTP ranges. Other supported containers fall back to a separate MediaExtractor with bounded HTTP byte ranges (30-second read budget, 32 MiB/128 requests, 2 MiB cache). Samples only beginning/ending windows and never seeks the active player. Image/PGS subtitles require OCR and are not treated as text; unsupported containers/range servers fall back safely.
- Searches installed subtitle sources and the official public OpenSubtitles v3 provider even when it is not installed. Queries use episode/movie identification, not stream URLs or media credentials. Prefer SDH labels and actual descriptive cues; evaluate candidate timing against embedded indexes before automatic use. Unknown alignment is rejected; a user-selected subtitle retains its explicit delay.
- Embedded packets without reliable cue ends are timestamp observations (1-ms intervals), explicitly distinguished from exact end boundaries in the AI instructions.
- Google text-only and visual requests now try the documented Gemini 3.6 Flash model after availability failures on 3.8 Flash. At most three analysis attempts/two probe attempts within overall deadlines; no model fallback for auth, quota or bad requests. Live diagnostics identify model, source selection, extracted counts, descriptions and rejected candidates without raw text/URLs/keys.

### Verification
- GitHub Actions run 40406708157 passed the universal Android build, selected acquisition/model-route/privacy and embedded timeline unit suites, 61 analyzer/review checks and ICU compatibility checks. Real embedded-container support, subtitle alignment accuracy and Google service access require device testing. The public subtitle provider returned HTTP 403 from the build environment, so provider access also needs a device check. The device log shows a real 503/UNAVAILABLE response even for the small probe, not a demonstrated lack of network connectivity.
- Text extraction remains best effort. No bitmap OCR, audio detector, adaptive video refinement or automatic AI publication is implemented.

## [0.3.6] — 2026-10-26

### Distribution
- Published signed version code 500015 through successful workflow 40381633679. Public APK full download matched SHA-256 22e2463971b525e554c3df8846d14b9e4fb672cce53443383208f198c575d426 (155997032 bytes).
- Downloader code 5813076 was verified against the immutable GitHub 0.3.6 APK URL. Release anchor bf4674580c05ba79d45f914669c45d091abbcd56 has identical non-workflow source to tested commit 7c173e0d126620a9dda36dbcf708ab9d144ba3ee.

### Added
- Player control button toggles a passive top-left live detection panel. Shows plain-language current stage, six recent sanitized actions/results, counts and age of the last update in Hebrew/English; remains visible when player controls hide. No pause, focus capture or automatic upload.
- A separate cloud-test button appears while debug is enabled. Explicitly sends a tiny text-only inference request (30-second deadline) using the configured key/model; discloses API quota/billing. Success verifies only the small request, not video detection or account tier.
- Structurally allowlisted Google error status/reasons, excluding response messages, metadata, URLs, identifiers and keys. Error bodies are bounded to 16 KiB; unknown values are dropped.
- Up to three attempts for HTTP 502/503/504 with exponential delay and jitter, within a four-minute overall analysis-request budget. Retries are visible in the journal/overlay. Authentication, billing/quota and other HTTP failures are not retried. Caller cancellation still stops work.

### Verification
- GitHub Actions run 40379095603 passed the universal Android build and selected transport/privacy/live-journal unit suites, including 503 recovery, bounded retries, cancellation, safe provider reasons and snapshot privacy. Passed 61 analyzer/review checks and ICU compatibility checks. Android TV layout, remote navigation and real API/device behavior require device testing.
- Sparse initial video sampling remains experimental; audio analysis, adaptive boundary refinement and automatic AI publication are not implemented.

## [0.3.5] — 2026-10-26

### Distribution
- Published signed version code 500012 through successful workflow 40372432090. Public APK full download matched SHA-256 0336e2ac6647aa5a7211b763aeb562c645c545ada80bf37ebe8dc5daccb21491 (155980648 bytes).
- Downloader code 6673362 was verified against the immutable GitHub 0.3.5 APK URL. Release anchor 403e0cc36b4fd2b40f5ea9e246a083bc77ed4975 has identical non-workflow source to tested commit a70fd2e73140e63577082a8185b7588fd358c584.

### Fixed
- Device log hfk22 reached 24 extracted frames, then failed with network_timeout after about 10.6 seconds. The cloud client set only the total deadline; OkHttp's 10-second read/write defaults still applied. Explicit budgets are now connect 20s, upload 60s, response read 120s and total 180s.
- Preserve timeout/network classification when a response body fails mid-read; classify the total call deadline as a timeout too. Cancellation still cancels the underlying HTTP call; retries and redirects remain disabled.
- Do not log an empty subtitle pass as a completed cloud analysis. Log the real visual cloud request and successful visual result with evidence/proposal counts.

### Verification
- GitHub Actions run 40369825483 passed the universal Android build, delayed-response/deadline/body-timeout/cancellation regressions and existing selected unit suites. Passed 61 analyzer/review checks and ICU compatibility checks. Device verification remains pending.
- The log proves a timeout, not its underlying network/provider cause. These longer budgets do not establish that the user's key or service access works.

## [0.3.4] — 2026-10-26

### Fixed
- A device-uploaded log contained only two session-start events. Removed journal resets from review identity rebinding (including duration/teardown changes); the player collector now starts its journal only when analysis can actually run.
- Remove duplicate session-start entries and preserve the completed log when leaving playback for settings.
- Separate native video extraction failures from Google network/response/output failures. Preserve prior HTTP failures when optional media extraction also fails.
- Classify cloud failures using fixed labels for DNS, TLS, timeout, connection, invalid response/output, output limit and blocked output; never include provider bodies, URLs or keys.

### Verification
- Published signed version code 500011 through successful workflow 40305728797; public APK download matched SHA-256 57cc1769d4edd1f5837f2870391bc7609491e9b669946ae14644d3dcc3c045ee.
- Downloader code 9544137 points to the immutable 0.3.4 APK. Release anchor 831850dd38280d2fbf59d42d1fe1fa368a0f56f9 was verified identical to tested source except approved workflow definitions.
- GitHub Actions run 40303335843 passed the Android APK build and selected unit suites, including journal continuity and safe failure classification. Passed 61 analyzer/review checks and ICU compatibility checks.
- The original cloud failure cannot be determined from the uploaded log because the detailed events were lost. A new device run is needed after this fix.


## [0.3.3] — 2026-10-26

### Fixed
- Natural playback completion no longer cancels an active evidence collector or rejects late review proposals.
- Reserve the end-of-episode review before remote analysis, preventing automatic episode navigation from winning the race. Closing the summary still releases navigation.
- Offer subtitle proposals before optional native frame extraction, which may be slow or unsupported.
- Preserve review edits when a visual result arrives after the review becomes visible.
- Preserve numeric Gemini HTTP failures instead of flattening them into an uninformative IOException; preserve successful subtitle proposals when visual analysis fails.

### Added
- Bounded chronological detection log with fixed action names, elapsed times, numeric evidence counts, settings flags and sanitized HTTP outcomes.
- Manual upload of the latest detection log to dpaste.org from diagnostics settings. Returns a copyable URL; requests 7-day expiry and discloses link access. No background uploads, credentials, response bodies or media/subtitle URLs.
- End-of-episode status summary when review mode has no proposals, including in-progress, existing reports, missing metadata, lookup failures, disabled cloud and unsuccessful analysis.
- Local sanitized detection status in screenshot/copy diagnostics. No API response bodies, keys or media URLs are included.

### Verification
- Published 0.3.3 (500010) through successful workflow 40300162329. Public APK download matched SHA-256 53a9af32dc89e0fc12cf5293a0b2fd5b66271ecdd372416e11efa46f53e65287.
- Downloader code 6153935 points to the immutable 0.3.3 GitHub APK.
- Direct GitHub artifact reconstruction avoids external staging hosts. Release anchor 0c9d3d2720df591c1b8c9ec96dc82924ed56ca1d differs from tested source only in approved default workflow definitions; application source equivalence was verified.
- GitHub Actions run 40283797963 passed the full Android build and selected unit suites, including completion identity, journal bounds/privacy and upload-link validation.
- Passed 61 analyzer/review checks and ICU compatibility checks. Synthetic dpaste upload and public readback succeeded.
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
