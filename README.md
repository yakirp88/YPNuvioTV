# YP Nuvio TV — IntroDB reporting

Independent fork of NuvioTV with recap, opening/theme song and credits reporting,
video thumbnails and boundary correction. Existing segment types are hidden.

**[Download test APKs](https://github.com/yakirp88/YPNuvioTV/releases)** ·
[Setup, build and validation](YP-INTRODB-README.md)

To install addons: **Addons → Manage from phone**, scan the QR on a phone on
the same network, paste the manifest URL and confirm on the TV. Official account
cloud sync is not configured in this build.

The changes and upstream are GPL-3.0. This is not an official Nuvio release.

---

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
