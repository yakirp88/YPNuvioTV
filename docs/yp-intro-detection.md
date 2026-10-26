# YP automatic segment detection

Current branch adds an opt-in Gemini visual/subtitle proposal pipeline and a
manual episode-end review/publishing path. The universal Android test APK now builds and its signature/package have been
verified. Native extraction, actual TV UI and live authenticated submission still
require device testing. The implementation notes below
record incremental stages; the final section describes the newest changes.

Base: official `NuvioMedia/NuvioTV`, tag `1.1.0-beta.8`, commit
`ce6ca448b9972d205ac2ba9f2a55a07743cc81f1`. Verified October 25, 2026.
The user explicitly replaced the earlier Nuvio VIP base with official Nuvio.
All existing GPL-3.0 license and attribution files remain in place.

## Implemented

- Settings → Integration → YP Intro Detection, with English and Hebrew labels.
- Opt-in, device-local evidence collection for the selected add-on subtitle.
- Reuses the existing subtitle downloader and SRT/WebVTT parser, for either
  internal playback engine. No changes to playback or existing skip intervals.
- Waits for a known VOD duration; excludes live playback. Cancels the collector
  when the selected track, episode, timing offset, enabled state or profile changes.
- English/Hebrew explicit opening, recap, closing and preview caption clues.
- Generic music remains a separate observation. Missing subtitles and subtitle
  gaps do not imply an intro; the last caption does not imply the end of the plot.
- Subtitle offsets are applied; invalid, overflowing or out-of-video ranges are
  rejected. Dialog between music cues splits the evidence.
- At most 20 JSON reports per profile, written atomically under `noBackupFilesDir`.
  Reports contain cue observations and hashed media identity, not subtitle text,
  source URLs, stream headers or API keys. Source version includes duration,
  media stream fingerprint, subtitle URL fingerprint and timing offset.
- Personal AI and IntroDB key entry, replacement and deletion. Keys are encrypted
  with the existing Android Keystore protected preference implementation, scoped
  per profile, excluded from cloud settings sync, and removed on profile deletion
  or account local-data reset through `ProfileScopedCredentialStore`.
- Settings show the most recent report's clue count and observed ranges.
- Per-profile submission mode: `REVIEW_AT_EPISODE_END` (default) or `AUTOMATIC`.
  Old or unknown values fall back to review. Changing the setting alone does not
  activate submission while the detector and publisher are unconnected.
- A reusable `IntroSegmentReviewDialog` component: start/end image pair and times
  for each verified segment, inclusion toggle, ±1 second boundary adjustment,
  continue without sending, and approve selected segments. Editing invalidates
  the affected image; approval stays disabled until both matching images are
  supplied. Sending disables editing and duplicate approval; failures stay open.

## Deliberately incomplete

This is an evidence collector, **not a completed AI detector or installable
standalone plugin**. It is integrated into the YP app branch because the player
has access to subtitle selection, timing, media duration and profile lifecycle.

The personal Gemini key can now power optional subtitle-only draft analysis using
`gemini-3.8-flash`. Cloud analysis is separately opt-in and off by default; its
settings disclose sending captions to Google and possible API charges. Only
bounded opening/ending caption windows are sent (up to 300 cues / 40,000 text
characters), without source URLs, stream headers, media IDs or account details.
Requests use a fixed HTTPS origin, a key header, no redirects/retries or logging,
a 60-second timeout and cancellation tied to track/profile/setting changes.
Structured output is range-checked, limited to eight non-overlapping proposals,
and rejected on incomplete or malformed results. Model suggestions remain
`unverified_subtitle_only` and never become eligible for publication.
The IntroDB key is not used yet. No live key was available, no paid calls were
made and live provider integration has not been verified.

There is no audio fingerprinting, video-frame model, Internet timestamp import,
skip-time generation, automatic seeking or IntroDB submission. Every report has
`status=evidence_only` and `can_auto_submit=false`. Observed caption timestamps
are not asserted to be segment boundaries. Preview is distinct from end credits.

The review dialog is a prepared UI component, not yet attached to the natural
episode-end/navigation lifecycle: there are no verified detection results or
boundary images to provide to it. Its callbacks require the future coordinator
to extract frames and perform authenticated submission. No fabricated segments
or placeholder images are presented as real detections.

When that coordinator is added, review mode must hold next-episode navigation
and post-play countdowns until review is dismissed or submission succeeds. Show
it once per natural episode completion and only for nonempty verified results;
do not trigger it on player errors or mid-episode source switches. An absent
image must not silently fall back to an unrelated artwork image. Automatic mode
requires calibrated detection, verified boundaries and no conflicting timings;
the choice itself does not bypass those checks.

## Next integration requirements

1. Validate the Gemini connection with a personal key and expose model selection. Use personal keys on-device;
   never embed a shared key or put credentials into plugin install URLs.
2. Add bounded, explicit cloud analysis opt-in, configurable request budget,
   cancellation and per-media caching. Subtitle data is untrusted input, never
   instructions. Model-reported confidence is not a calibrated quality score.
3. Extract audio/frame evidence and refine boundaries. Verify against the actual
   video release; duration alone is not a release identity.
4. Evaluate on a labeled set including cold opens, opening credits over plot,
   shortened finales, dubbed themes, recaps and post-credit scenes. Measure
   boundary error and false skips separately from classification accuracy.
5. Add an IntroDB submission queue only after validating publication policy,
   API limits and calibration. Respect existing confirmed-absent flags, avoid
   duplicate reports, and do not overwrite existing timings with weak estimates.

## Verification

Dependency-free production analyzer checks (requires JDK 11+):

```sh
mkdir -p /tmp/yp-intro-check
java com.sun.tools.javac.Main --release 11 -d /tmp/yp-intro-check \
  app/src/main/java/com/nuvio/tv/core/intro/SubtitleEvidenceAnalyzer.java \
  app/src/main/java/com/nuvio/tv/core/intro/IntroSubmissionMode.java \
  app/src/main/java/com/nuvio/tv/core/intro/AiSegmentProposal.java \
  app/src/main/java/com/nuvio/tv/core/intro/IntroDetectionPhase.java \
  app/src/main/java/com/nuvio/tv/core/intro/VideoSamplingPlan.java \
  app/src/main/java/com/nuvio/tv/core/intro/SegmentReviewRange.java \
  tools/intro-evidence-check/SubtitleEvidenceCheck.java
java -cp /tmp/yp-intro-check SubtitleEvidenceCheck
```

60 checks pass. They exercise the actual production Java implementation,
including submission policy and boundary range editing.
English/Hebrew resource XML names and formatting were checked, as was
`git diff --check`.

The Android Gradle build and on-device/TV D-pad, profile switching, key persistence,
deletion/reset and both playback engines have **not** been verified. Running
`:app:testFullDebugUnitTest` was blocked while downloading Gradle 8.13 by
`java.net.SocketException: Network is unreachable`. No APK is claimed or produced.

Gemini API contract checked October 25, 2026: https://ai.google.dev/api/generate-content and https://ai.google.dev/gemini-api/docs/generate-content/structured-output .

## Playback status indicator

A non-focusable absolute top-right popup shows an animated ring and
“Locating skip points” / “מאתר נקודות דילוג” while subtitle detection is active.
It starts after playback begins when collection is enabled, including an
8-second wait for a subtitle track/duration. Selecting a track starts analysis;
track/profile changes cancel obsolete work. The indicator hides on completion,
failure, live playback, disabling collection, or natural episode end. Media
analysis is deliberately quiet. `AWAITING_MEDIA_ANALYZER` is a handoff state,
not a claim that media analysis is implemented or running. No audio/video worker
is connected yet. Android placement, remote focus and animation remain untested
on a built APK/device.

## Coarse visual analysis and IntroDB preflight

The collector now requests IntroDB before processing a known IMDb episode/movie.
Existing segment entries and confirmed-absent intro/recap/outro flags suppress
analysis for their types. Timeout, transport failure, 429, unexpected status or
missing success body stop analysis instead of being interpreted as missing data.
404 permits missing-segment analysis. Results are cached per episode for the
player session. Unknown identity is deferred. A fresh lookup immediately before
publication remains to be implemented with the submission coordinator.

After subtitle proposals, opted-in cloud analysis enters ANALYZING_MEDIA and
hides the spinner. A separate MediaMetadataRetriever decoder samples up to 24
480x270 JPEG frames across missing opening/ending windows, without seeking the
playback decoder. The frame payload includes approximate requested times and
captions but no source URLs or stream headers. No subtitle track is required
for the visual fallback. Only missing segment types are requested and accepted
from Gemini. Result ranges remain provisional; sparse sampling does not justify
auto-submission. This is video-image analysis; audio is not analyzed yet.

Native retrieval supports only some stream/container combinations, may increase
network/decoder load, and has no hard interruption facility. Cancellation is
checked between frames and native resources are released when calls return.
The one-minute sampling budget is checked between native calls, not a guaranteed
wall-clock timeout. UI consent text now includes sampled video images.

Live Gemini/IntroDB, Android compilation, native extraction, simultaneous
playback and TV UI behavior have not been validated. The review dialog, exact
boundary-frame extraction and publisher are still unconnected. Earlier notes
that describe media analysis as unconnected refer to the previous foundation;
this section records the current coarse visual pipeline.

## Manual episode-end review and publisher

`IntroReviewSession` now receives AI proposals for review-mode profiles and
extracts boundary images using a separate decoder. The episode-end dialog shows
proposals for human verification, not automatically verified detections. Time
edits invalidate affected images; replacement images must load before approval.
Natural end navigation and non-user-initiated next-episode playback are held
while review is pending. Post-play recommendations/countdowns are cleared while
pending and resume after dismissal. Explicit user navigation can still leave.
Results arriving after episode completion are not allowed to reopen a departed
player. Source/offset/duration changes clear obsolete review state and jobs.

Approval re-reads IntroDB per selected segment. Existing or confirmed-absent
segments are skipped. Unknown preflight outcomes do not publish. POST uses a
separate non-logging, non-redirecting, non-retrying client and the current
profile's personal IntroDB key, with a 20-second request timeout. Successfully
handled segments are removed immediately; ambiguous or failed POSTs cannot be
retried within the same player session. Duplicate prevention is session-local,
not durable idempotency across restarts or atomic protection against concurrent
contributors. The publisher does not yet persist a submission journal.

The official public IntroDB origin is now the fallback when the build has no
INTRODB_API_URL; the earlier localhost fallback prevented unconfigured builds
from checking coverage. Automatic mode still never publishes uncalibrated model
proposals. Audio analysis, exact automated boundary validation and durable
submission recovery remain unfinished. No live submission or paid API request
was performed during implementation. Existing 60 production Java core checks
remain the verification available; new Android coroutine/UI/network paths have
not been executed or compiled in this environment.

## Verified test build — October 25, 2026

- Universal APK assembled successfully with JDK 17, Gradle 8.13, Android platform
  36 and build-tools 35/36. Uses existing prebuilt media AARs; configure-on-demand
  avoids initializing the unused native FFmpeg source module.
- Test app identity: `com.nuvio.tv.yp`, label/launcher labels `Nuvio YP`,
  version `1.1.0-beta.8-yp-intro.1`, version code 1071. It installs separately
  from official release and official debug builds.
- Debug builds use a dedicated Android debug signing key rather than the upstream
  release key. Preserve the saved `Nuvio-YP-debug-signing.keystore` for future
  updates to this test installation; never commit that private key to source.
- `-PypUniversalOnly=true` disables ABI splits and their universal-output flag
  together. The resulting single APK includes armeabi-v7a, arm64-v8a, x86 and
  x86_64. Minimum Android API is 24; target API is 36.
- APK Signature Scheme v2 verification passed and ZIP entry CRC checks passed.
  Artifact: `Nuvio-YP-Intro-v0.1-universal.apk`, 163568669 bytes.
  SHA-256: `7ea8e587b69ec3cf63680b5ebf8db6e1245d432969e424de2e9d510cfa27c2b4`.
- 18 `SkipIntroVisibilityRulesTest` unit tests passed, with zero failures, errors
  or skips. The 60 existing dependency-free detection/review checks also passed
  for the production core used in this build.
- A public read request to IntroDB succeeded and its segment/confirmed-absence
  response fields matched the implemented model. No timestamps were submitted,
  and no live Gemini request was made.

Reproducible build settings for this constrained environment:

```sh
./gradlew -PypUniversalOnly=true -Pksp.incremental=false -Pkotlin.incremental=false   :app:assembleFullDebug --configure-on-demand --max-workers=1
./gradlew -PypUniversalOnly=true -Pksp.incremental=false -Pkotlin.incremental=false   :app:testFullDebugUnitTest --tests '*SkipIntroVisibilityRulesTest'   --configure-on-demand --max-workers=1
```

The final APK run excluded repeated lintVital tasks to limit memory use; vital
lint had completed in the preceding multi-output build. Signing and packaging
were verified independently. Earlier failed runs exposed an unrelated incomplete
temporary optimization profile and corrupted generated caches; those generated
items were moved aside without deleting tracked source or the original profiles.

This is an experimental installation candidate, not an end-to-end validated TV
release. Configure add-ons and personal Gemini/IntroDB keys in the new app.
Cloud analysis remains opt-in; review at episode end is the test path. Automatic
publication stays disabled for uncalibrated proposals. Audio analysis is not yet
implemented. Real Google TV playback/decoder contention, preview frame accuracy,
API permissions and authenticated publication still need testing on the device.

## QR login correction — test build v0.2

The original v0.1 build lacked Nuvio backend configuration. The login UI existed,
but that did not verify usable pairing. v0.2 includes the official website's
public API origin and public anonymous client configuration, obtained from the
published login client and validated to have the `anon` role. It includes no
user session, private service key, Gemini key or IntroDB personal key.

Both the current `start_device_login_session` and legacy `start_tv_login_session`
RPCs returned a valid anonymous pairing session during verification. The current
flow returned a user code and HTTPS verification URL at `nuvio.tv/link`. No user
account was signed in and no approved token exchange was performed. Actual phone
approval and TV rendering still need on-device testing.

Version code is 1072 and version name is `1.1.0-beta.8-yp-intro.2`. The package
identity/signing key are unchanged, so v0.2 can update v0.1. A versioned onboarding
flag reoffers QR once to an unsigned-in user who previously skipped the broken
v0.1 prompt. Existing full accounts continue automatically without being reset.
The public backend configuration lives in ignored local.dev.properties for the
build; do not commit local configuration or private credentials.

v0.2 universal APK built successfully, passed APK Signature Scheme v2 verification
and ZIP CRC validation. Its DEX contains the expected public backend configuration
and the v2 onboarding migration flag. SHA-256:
`34d8c1b1b3743222ae9d2c7a6aaee58cc5b4339d8820a0b314b716d3b8e916a2`.

## 0.3.2 crash-diagnosis update

Device feedback: playback works with both detection options off, but crashes with
subtitle collection on and cloud analysis off. This narrows the issue to the
local path; no exception trace has yet confirmed its root cause.

The passive ring is now an absolute top-left child of the player Box, rather
than an Android Popup. Optional coroutine failures are contained, and a local
sanitized diagnostic report captures class/frame structure without exception
messages, credentials, transcripts or authenticated URLs. A recovery dialog can
show a report after restart, and Settings → YP segment detection provides the
same paginated screenshot/copy view. Android 11+ exit metadata can distinguish
native/ANR/low-memory terminations when supplied by the OS. No raw OS trace is
read and nothing is uploaded automatically.

The original 0.2 signer was recovered privately and its certificate fingerprint
was compared to config/yp-signing-sha256.txt. Local signing followed by a
checksum/certificate-verified GitHub release import avoids blocking this update
on web sign-in for Actions secret setup. The key must not appear in artifacts.
