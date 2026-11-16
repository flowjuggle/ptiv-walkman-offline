# Kattus — protected PTIV Watch7 baseline
Date: 2026-11-16

## Confirmed working on the watch (historical 2026-11-14/15 QA)
- Hardware: Samsung Galaxy Watch7 SM-L300.
- On-watch tested app: `live.ptiv.watch.neonpreview` ("P.T. Architech Neon Test").
- Tested: 12 PTIV tracks each started playback; library, controls, shuffle, lyrics-screen navigation; side `+`/`-` controls changed media volume 9 → 10 → 9.
- Face `live.ptiv.faces.neonpearl` launches `live.ptiv.watch.neonpreview/live.ptiv.watch.WatchActivity`.
- Original app `live.ptiv.watch` and other faces must remain installed and unchanged.
- Verify the watch's currently installed APK hash, version and signing fingerprint before calling any repo snapshot *binary-identical* to the working installation.

## Immutable recovery source snapshot
`archive/kattus-neon-working-source-20261116`
created from Git commit `7b6be13d74d7ec1c802646d68673a1df5aaf1fdd`.
This is a source checkpoint, **not** proof of the exact installed APK/signing certificate or a backed-up app-data image. Do not rebase, merge experimental code into, or force-push this checkpoint.

## Separate Kattus-named candidate
This branch (`feature/kattus-watch7-isolated-app-20261116`) uses:
- Application label: **Kattus**
- Isolated Android application ID: `live.ptiv.watch.kattus`.
- Android namespace stays `live.ptiv.watch` so existing Java Activity/Service classes resolve.
- Version is deliberately named `0.2-kattus-isolated-candidate` until separately built, installed and tested.
- The Neon Pearl watch face still launches the verified **neonpreview** package. Do not retarget or replace that face automatically.

## Never break the current working watch app
1. Do **not** uninstall, overwrite, retag, clear data for, or update `live.ptiv.watch.neonpreview`.
2. Do **not** update `live.ptiv.watch` or any installed PTIV watch face while preparing Kattus.
3. Build and install Kattus **side-by-side**; check package identity and signing before installation.
4. Test Kattus separately: all 12 song starts, play/pause, next/previous, background playback, volume `+` and `-`, library, shuffle, lyrics state, screen wake, and watch-face routing.
5. Only after passing can the Kattus-named app be called verified-working. Retain the verified neonpreview as recovery fallback.
6. Future experiments must use new independent application IDs such as `live.ptiv.watch.experimental2`, not the Kattus ID or the original neonpreview ID.
7. Store APK SHA-256, signing certificate digest, exact source commit, test receipt, full app version and watch-side installation results. Do not invent approval or playback results.

## Current access blocker
On 2026-11-16 the previously authorized Chromebook Plus Linux host `penguin` was **offline** from its Remote Desktop Commander connection. No on-watch Kattus install or runtime test has been performed under this request.

No Too Lost changes, no Musixmatch submissions, no paid service, no source-audio modifications.
