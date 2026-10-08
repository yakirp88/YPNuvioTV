# YP Nuvio — IntroDB reporting fork

Based on the official `NuvioMedia/NuvioTV` `dev` branch at
`6377bee8cc644ecd8048a771ced61f06bcdf7946` (1.1.0-beta.5).
הגרסה מוסיפה דיווח על תקציר, פתיחה וסיום מתוך הנגן: כפתור דגל שומר מיד
את זמן ההתחלה, בחירת סוג הקטע, כפתורי סיום וביטול צפים, וחלון לתיקון
הזמנים עם תמונות. סוגים שכבר קיימים במאגר מוסתרים. מפתח API אישי
מוגדר בהגדרות הניגון ונשמר מוצפן במכשיר.

This is an independent GPL-3.0 fork, with package ID `com.ypmadridista.nuvio`
(`com.ypmadridista.nuvio.debug` for debug builds). It installs alongside Nuvio.

## What is implemented

* A single flag button in the internal player's main controls. Its click
  records the current engine position immediately, before choosing a type.
* A TV-friendly type dialog with **Opening / theme song**, **Recap** and
  **Ending / credits**. Existing IntroDB types are omitted. Movies expose
  only credits because IntroDB's movie API supports outros/post-credit scenes.
* Floating Finish and Cancel buttons while playback continues. Finish records
  the end and pauses playback. Natural EOF finishes an active recording.
* Review dialog with start/end timestamps and separate 320px video thumbnails.
  ±0.5s and ±1s corrections seek precisely and refresh the selected thumbnail.
* Only the video surface is captured; controls and dialogs are not captured.
  No images are uploaded to IntroDB. Unsupported/protected surfaces display
  a clear "Preview unavailable" message instead of an old image.
* Existing segments are checked independently of automatic skipping. A failed
  read never enables reporting. Public timestamps refresh every minute while
  idle, and the selected type is checked again immediately before submitting.
* After successful submission, that type is hidden locally even if public
  aggregation has not updated yet. All three present means no report button.
* Offline/error drafts persist locally. On the next report click, the new click
  still records its time; the type dialog separately offers Resume saved draft.
  Save draft and close preserves an unsuccessful report. Cancel discards it.
* Skipping, post-play and watch-progress writes are suspended during reporting
  so precision preview seeks do not change progress or start another episode.
* After send/cancel, playback returns to the point at which Finish was pressed,
  and resumes if it was originally playing.
* Hebrew and English strings, remote focus, Settings → Playback → Skip segments
  toggle and personal IntroDB API-key input. Key is AES-GCM encrypted using
  Android Keystore, never synced, logged, or bundled into the APK.
* Reporting requests use a separate HTTPS client with redirects disabled,
  without stream authorization headers or HTTP logging.

## Setup on Google TV

1. Install the debug ARM64 APK from the build artifacts. It appears as YP Nuvio.
2. Open Addons → Manage from phone, scan the TV's QR using a phone on the same
   network, and paste your addon's manifest URL into the local management page.
   Confirm the proposed change on the TV and keep its management screen open.
   This works without signing in to the official Nuvio website. The independent
   build does not configure official account/cloud synchronization.
3. Open Playback settings → Skip segments → IntroDB reporting API key.
4. Create your own key at https://introdb.app/ and enter the `idb_…` key.
   The save action stores the key; the service validates it when submitting.
5. Play an IMDb-identified series episode in the internal ExoPlayer or MPV.
6. Show controls, press the flag at the start, choose a missing type, then Finish.
7. Correct boundaries if necessary and press Send to IntroDB.

The flag is deliberately hidden while a successful existing-segment check is
pending, for live media, unresolved episode identity, unsupported special-season
numbering (season 0), disabled reporting, and when every eligible type exists.
A key can also be entered on first use; after saving it, press the flag again
at the actual segment start.

## Build locally

Requires JDK 17, Android SDK platform 36, Android build tools 35+, and NDK
29.0.14206865. Android Studio/Gradle can install missing SDK components.

```
./gradlew :app:assembleFullDebug
```

Windows:

```
gradlew.bat :app:assembleFullDebug
```

Debug uses the Android debug signing key. Install the ARM64 or universal APK
from `app/build/outputs/apk/full/debug/`. Keep the signing key for future
updates of the same installed fork. Do not distribute your private API key.

IntroDB's public endpoint is configured by default. Optional upstream services
(cloud login/sync, TMDB, Trakt, etc.) require their own build configuration as
explained by the upstream project; the official application's private build
configuration is not part of this source distribution.

## GitHub fork and automated build

The GitHub fork is https://github.com/yakirp88/YPNuvioTV.
The complete source and GPL license are available in the repository.

The `YP IntroDB build` workflow is manual (`workflow_dispatch`). It produces
signed debug APKs and runs the reporting policy unit tests. Its Publish release
option creates a GitHub prerelease with public APK download links after a
successful build. It can be run once these changes are in your fork.
Automatic upstream APK updates are disabled for this independent signature.

## Validation and limits

* `:app:assembleFullDebug` completed successfully, producing all four ABI APKs
  and a universal APK. APK v2 signature and independent package/version were
  verified. Android Lint vital report: no issues found.
* Full application Kotlin/Java compilation passed. Six reporting-policy JUnit
  tests passed with zero failures.
* 19 pure Kotlin reporting-policy checks and a Kotlin lifecycle test exercise
  capture-before-type-selection, existing-type blocking, paused review,
  corrected thumbnails, restored playback position, duplicate race checks,
  failed-read lockout, offline draft persistence and explicit draft cancellation.
* Lifecycle checks use fake player, bitmap, metadata and network components;
  they verify the real coordinator but do not verify Android decoding or UI.
* Real thumbnail rendering, remote navigation, encrypted key persistence, and
  authenticated submission still need testing on a Google TV device.
* Thumbnail precision is bounded by source frame rate, decoder, and seek
  support. A timeout produces no thumbnail rather than a stale one.
* Existing checks reduce duplicates. A different contributor can still submit
  between the final GET and POST; server-side duplicate handling is authoritative.
* IntroDB acceptance/moderation and segment-length validation remain server-side.
  Recap means "previously on", not a trailer or next-episode preview.

## Licensing

Upstream and changes: GNU GPL v3. See LICENSE. This fork does not claim to be an
official Nuvio release.
