# PTIV Watch Face Format collection — isolated prototype

Three separate Watch Face Format version 1 packages for Wear OS Galaxy Watch7:

- PTIV Pearl Triangle — live.ptiv.faces.pearl — teal / dark.
- PTIV DUMBO Night — live.ptiv.faces.night — violet / dark.
- PTIV Signal Pulse — live.ptiv.faces.pulse — bright lime / dark.

Each contains a direct-launch Pearl Triangle tap target for the original player component `live.ptiv.watch/live.ptiv.watch.WatchActivity`. These WFF packages contain **no application code**, **no music**, and **no changes to the existing player**.

## Safety gates

1. Build debug test APKs with the isolated GitHub Actions workflow.
2. Check the APK application ID against the expected `live.ptiv.faces.*` name and verify SHA-256 before install.
3. Install only those new packages; do not use `adb install -r` on the original Walkman.
4. Verify original `live.ptiv.watch` and all 12 PTIV offline file hashes after each install.
5. Select a test face on the watch and test time, direct-launch tap, display in ambient mode, and face-switching without modifying the original player.

These are prototypes; no Google Play release, Too Lost changes, or music publishing.
