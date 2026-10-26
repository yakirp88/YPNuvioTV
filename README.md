> **Nuvio YP AI fork** — development branch: `yp/ai-intro`.
> [Download 0.3.5 APK](https://github.com/yakirp88/YPNuvioTV/releases/download/yp-ai-v0.3.5/Nuvio-YP.apk) · [Releases](https://github.com/yakirp88/YPNuvioTV/releases) · [Changelog](CHANGELOG.md) · [Build/signing guide](YP-AI-README.md)
> Version 0.3.5 fixes the short cloud read/write defaults, preserves network timeout classification and logs actual visual requests/results. Android build and delayed-response/deadline/cancellation/body-timeout tests passed. Device cloud-analysis verification is still needed. Audio detection and automatic AI publishing are not implemented.

> Downloader code: **6673362** — YP AI 0.3.5, version code 500012. Updates previous YP AI versions in place using the same signing certificate.

<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="Nuvio" width="300" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioTV/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## Get Nuvio TV

- [Android TV on Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app)
- [Android TV APK](https://github.com/NuvioMedia/NuvioTV/releases/latest)

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioTV.git
cd NuvioTV
./gradlew :app:assembleFullDebug
```

Nuvio TV is built with Kotlin, Jetpack Compose, TV Material 3, and Media3. Development requires Android Studio, a JDK, and the Android SDK.

## License

[GNU General Public License v3.0](./LICENSE)
