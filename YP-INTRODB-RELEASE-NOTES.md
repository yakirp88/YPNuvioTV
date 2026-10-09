# Minimal IntroDB report controls

Version `1.1.0-beta.5-yp-introdb.4` (1071).

Choose a report type after clicking the player flag. Recording shows only a small circular flag opposite the clock, with a rotating blue arc. The icon uses the same Material Flag as the player controls. Playback and seeking remain available; controls hide normally. Click the player flag again for Finish report / Cancel. Back dismisses that menu without discarding the report; during recording it hides player controls.

Review uses a compact dialog with two frame previews and independent minus/plus controls. Select the adjustment size in two rows: 1 / 5 / 10 seconds, then 1 / 5 / 10 minutes. Start and end remain clamped to valid video bounds. Confirmation pauses playback and restores the finish position on exit.

Reporting lifecycle checks passed locally; the CI workflow runs eight reporting JUnit tests, vital lint and APK assembly. Physical TV layout and focus still require device testing.

# YP Nuvio — IntroDB reporting test build

Choose `app-full-universal-debug.apk` when the device architecture is unknown.
This independent installation appears as YP Nuvio, alongside the official app.

To install addons, open **Addons → Manage from phone** on the TV. Scan the QR
using a phone on the same network, paste the addon's manifest URL into that
local page, and confirm the proposed change on the TV. Keep the management
screen open until finished. This does not require the official Nuvio account.

Official account/cloud synchronization is not configured in this independent
build. Addon settings are stored in the fork's own installation.

Configure your own IntroDB API key in Playback settings before submitting.
Reports support recap, opening/theme song and ending/credits for series;
movies support ending/credits only. Existing segment types are hidden.

Build, reporting policy tests and Android Lint passed locally. Real Google TV
thumbnail capture, remote focus and authenticated submission still need device
testing. See YP-INTRODB-README.md for source/build details and limitations.

Source and modifications are GPL-3.0; this is not an official Nuvio release.
