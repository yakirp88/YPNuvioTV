# Nuvio YP AI

Source branch: `yp/ai-intro` in https://github.com/yakirp88/YPNuvioTV.
The existing `dev` branch contains separate native IntroDB reporting work and is preserved.
This branch is based on official NuvioTV `1.1.0-beta.8`, commit
`ce6ca448b9972d205ac2ba9f2a55a07743cc81f1`. GPL-3.0 and upstream notices apply.

## Downloads and versions

Use GitHub Releases, choosing a release whose title starts with **YP AI**.
`yp-ai-v0.2.0` imports the previously verified APK, with checksum verification.
Each subsequent source push runs tests and builds an APK; a prerelease is published
only when a persistent signing key is configured. Release tags are unique and are never overwritten.
Download `Nuvio-YP.apk` from the release assets. A short Downloader code can point to
that public asset; ChatGPT Library links require account access and are not direct APK URLs.

`yp-version.properties` controls the product version. CI adds its run number to
the version code and `.build.N` to its name. Update CHANGELOG.md for every meaningful change;
change the product version before a new release series. No automatic claim of device verification is made.

## One-time signing setup

In repository Settings → Secrets and variables → Actions, add
`YP_DEBUG_KEYSTORE_BASE64`: the Base64 encoding of the **same** saved
`Nuvio-YP-debug-signing.keystore` used for 0.2. Its alias is `androiddebugkey`,
and both Android debug passwords are `android`. The private keystore must never
be committed, attached to a public release or put in an Actions cache.
Using this same certificate preserves the ability to update 0.2 in place.
The workflow verifies it matches `config/yp-signing-sha256.txt` before distribution.

On Windows, after installing GitHub CLI and running `gh auth login`, run:

```powershell
powershell -File tools/yp/configure-signing.ps1 -KeystorePath "C:\path\Nuvio-YP-debug-signing.keystore"
```

This sends the saved key directly to the private repository secret; it does not
print it or add it to the source. The next source push starts a signed release build.

Without the secret, CI may build a disposable debug APK for checking compilation,
but it does not publish that APK as a release. A short-lived Actions artifact
retains it solely as input for private signing with the original saved key.
Do not install or distribute that intermediate artifact. Personal AI keys stay on the user's device.
`config/yp-public-client.properties` contains only public anonymous Nuvio client settings.

## Local build

Use JDK 17 and Android SDK 36. Copy `config/yp-public-client.properties` to
`local.dev.properties`, then run:

```sh
./gradlew -PypUniversalOnly=true -Pksp.incremental=false -Pkotlin.incremental=false \
  :app:testFullDebugUnitTest --tests 'com.nuvio.tv.ui.screens.player.SkipIntroVisibilityRulesTest' \
  :app:assembleFullDebug --configure-on-demand --max-workers=1
```

## Current feature state

AI suggestions are experimental. IntroDB is checked first; a failed lookup does
not permit blind duplicate submission. Manual end-of-episode review includes
boundary images and corrections. The automatic setting is stored, but automatic
publishing is disabled until confidence is calibrated. Audio analysis is not implemented.
Actual TV playback, phone approval and authenticated reporting need device verification.

## Development rule

Commit each completed change to `yp/ai-intro`, update CHANGELOG.md, push, inspect
the Actions result and verify its release asset before sharing a download link.
GitHub can build pushed commits automatically; it cannot sync uncommitted local edits.
