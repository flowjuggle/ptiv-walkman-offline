# PTIV Walkman — Wear OS standalone experimental build

Target: Galaxy Watch7 / Wear OS (Android 11+). Standalone Android watch package: `live.ptiv.watch`.

This is **a test project**, not a device-verified release. It does not depend on the iPhone/Samsung phone to play tracks **once the audio files are on the watch**. There is no cloud music upload.

## Included in the watch test build
- On-watch local audio discovery through Android MediaStore, with READ_MEDIA_AUDIO permission.
- On-watch play/pause, next, previous; continuous play and shuffle; standard Android MediaSession/watch controls.
- Foreground media playback service with a playback notification.
- .LRC lyrics import from a supported system file picker and synchronized single-line display. The lyrics are **never generated or altered**.
- Compact dark, neon-green, retro cassette-style UI with animated playback status.

## Not yet proven / not included
- **No watch device install or playback verification yet.** Build automation compiles the APK, not an end-to-end watch test.
- **No automatic phone-to-watch music transfer yet.** Import music to the watch using Galaxy Wearable music transfer or an authorized ADB file push; confirm it appears in the watch's music collection.
- Wear OS file picker may not be available on every watch firmware. If unavailable, .LRC import from watch UI is blocked, not silently simulated.
- Animated visual status does not claim to be a real-frequency-spectrum analyzer; the phone PWA contains the real Web Audio spectrum.
- No lyrics, audio recordings, API credentials or private verification receipts are bundled in the public source APK.
- The phone PWA and the watch app maintain **separate local libraries**; no streaming, NAS dependency or account setup needed for watch audio already copied there.

## Build
GitHub Actions → `Build PTIV Watch APK (test only)` → Run workflow. It builds using JDK17, Android SDK35, Gradle8.9, and AGP8.7.3. Debug APK is a GitHub Actions artifact; debug signing isn't a Play Store release.

## Installation testing
1. Enable developer options + wireless debugging on the watch, pair via authorized ADB on BOOK or YOG.
2. Download APK from the completed GitHub Actions artifact to the computer.
3. Run `adb install -r app-debug.apk` for the paired watch only.
4. Transfer a permitted music file to watch Music storage via Galaxy Wearable or ADB.
5. Open PTIV Walkman Watch, grant audio permission; test play/pause/next, continuous/shuffle, screen off, Airplane Mode, and local LRC display.
6. Record any codec/permission/background issue before declaring device acceptance.

Original audio and verified lyrics remain unchanged. No Too Lost/Musixmatch publishing.
