# P.T. Architech Walkman — 2027 control UX mini tutorial

## Galaxy Watch7 test gestures
1. **Play:** Tap Play; select one of the catalog-approved on-watch recordings from the list.
2. **Volume (two fingers):** Place both fingers simultaneously anywhere in the player except the keypad. Slide both to the **right to increase** or **left to decrease**. Approximately 29 dp per level, maximum five levels per gesture. Separate fingers, then repeat. Native system music volume is adjusted; speakers/Bluetooth volume response varies. Cosmic mode also has visible + / − buttons.
3. **Cosmic Karaoke:** Triple tap a quiet part of the screen (outside buttons) within approximately one second, or tap **COSMIC KARAOKE**. Triple tap again or tap **BACK** to leave.
4. **Lyrics:** Correct LRC lines display at their provided cue times. If no LRC is imported, **NO VERIFIED LRC YET** remains visible. The cosmic nebula is procedural animation, **not a microphone-powered or real-time audio-frequency spectrum**.
5. **Speaker:** Select **Speaker / earbuds: pair Bluetooth**, then use watch OS pairing controls.
6. **Guide:** Tap **? 30-second watch controls** any time for this tutorial.
7. **Offline:** Watch uses only local watch recordings that match an explicit P.T. Architech catalog entry AND its approved file origin. The phone companion is for transfers and metadata; watch playback never needs phone once files are installed.

## Compatibility and 2027 design review
Apple Watch: Crown adjusts volume and Now Playing controls the active output device. Pixel Watch: Crown and media controls, with user reports of accidental volume jumps. Samsung Watch7: touch bezel, volume page and device-specific system media controls. Google Wear OS media guidance: limit media-screen actions, show output route and explicit volume control, restrict AOD movement/pixels.
Research references:
- https://developer.android.com/design/ui/wear/guides/patterns/media
- https://www.samsung.com/us/support/answer/ANS10003406/
- https://support.apple.com/guide/watch/now-playing-apd4ea5db227/watchos
- https://developer.android.com/training/wearables/data/overview
- https://www.reddit.com/r/PixelWatch/comments/1qb7t8b/whose_idea_was_it_to_bind_volume_to_the_crown/
- https://www.reddit.com/r/GalaxyWatch/comments/1fbl6vr

## Companion
The phone and watch apps use the same Android application ID and **must be installed from the same signed build** for Wear OS Data Layer sharing. The native Samsung companion is a distinct APK from the browser-installed iPhone/Galaxy PWA. It selects an exact catalog track, permits a user-chosen MP3/M4A and approved LRC, and queues an encrypted Wear OS asset transfer. A queue receipt is not proof of on-watch verification: the watch must acknowledge a checksum match. The Watch receiver validates catalog ID, title, album and SHA-256, then imports via scoped Android media storage. It does not touch Too Lost.

## Acceptance gates
- Build and sign both APKs in the same CI job.
- Confirm install and permission grants on watch + Samsung phone.
- Prove only catalog entries are played; unrelated AutoSync media excluded.
- Test volume slider with a paired Bluetooth speaker at low initial volume; check both directions and cap/feedback.
- Test rapid triple taps without accidentally activating playback buttons.
- Test karaoke screen at 40/44 mm, timed-lyrics vs missing-LRC states, AOD/pause/battery, and 30-minute thermal use.
- Test phone-to-watch MP3 and LRC transfers end-to-end with checksum and observable receipt. No success claims before receipt.
- Ensure input audio master and original lyric source are preserved, no publisher metadata changes.
