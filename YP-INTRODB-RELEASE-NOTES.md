# Compact IntroDB report HUD

Version `1.1.0-beta.5-yp-introdb.5` (1072).

A small translucent popover anchors immediately above the native player flag. Choose Opening / Recap / Credits without a large dialog or timestamp details. Clicking the active flag opens Finish report / Cancel above that same button. The player keeps normal seeking and the corner flag indicator.

Finish pauses the video and shows only two clickable frame thumbnails, Cancel and Confirm and report. Select a thumbnail to seek the paused main video to that boundary and open a subtitle-sync-style calibration ruler. Left/right taps adjust 0.5 seconds; holding a direction accelerates through 2 / 10 / 30 second steps. OK or Back returns from calibration. Repeated input keeps only one frame decode in flight and follows the latest requested timestamp. Frame availability and seek speed depend on the stream/player engine.

Confirm and report or Cancel returns playback to the current marked END timestamp, including end corrections. Transmission waits for preview work to settle. Existing types and concurrent submissions remain blocked. Errors preserve retry/key/draft recovery controls only when needed.

Local coordinator checks passed for direct paused boundary selection, repeated input, pending-send blocking and cancellation back to the corrected end. CI runs reporting policy/transport unit tests, vital Android lint and APK assembly. Actual TV placement, remote focus, hold-repeat behavior and engine frame latency still need device testing.

This is an independent GPL-3.0 debug test build, not an official Nuvio release. Choose app-full-universal-debug.apk. A CI debug signing key may differ between builds; preserve addon URLs and your IntroDB key before removing an earlier fork installation if Android rejects the update. Removing the fork clears its local data.

Official account/cloud sync is not configured. Addons can be managed using Addons → Manage from phone on the same network. Configure your own IntroDB API key in Playback settings.
