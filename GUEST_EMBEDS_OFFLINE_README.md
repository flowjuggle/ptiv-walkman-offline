# PTIV Walkman v4 — guest embeds + personal offline music
_Status: QA passed in Chrome on YOG; Samsung/iPhone touch and background media acceptance pending._

## Two sources, two behaviors

**My Offline Music** — use **Add my own offline music**, Choose music files, Choose folder or Import ZIP. All audio bytes are saved inside the device's browser IndexedDB. Login to P.T. Architech is not required. The browser may evict storage under space pressure; preserve original masters separately. File formats depend on device codec support. Switching to Airplane Mode after importing a file is the best acceptance test for offline playback.

**Embedded Players** — paste the original provider URL or its official Share → Embed iframe code, add an optional label and tap **Save embedded player**. Saved labels and URLs remain in localStorage. Playback is by third-party iframe and needs network access. Some providers require their own login or paid entitlement, and may offer only previews in an embed. The app cannot bypass access controls. Embeds are not offline files and are not backed up to PTIV servers.

Supported embed sources, from strict validated URLs only:
- YouTube videos / playlists (privacy-enhanced youtube-nocookie player)
- Spotify tracks, albums, playlists and podcasts
- SoundCloud tracks/playlists; original player iframe supports api.soundcloud.com track/playlists IDs
- Apple Music songs, albums and playlists
- Bandcamp official iframe codes from Share → Embed (Bandcamp album pages alone cannot yield an embed ID)

Never inject user-supplied raw HTML, scripts, javascript: or insecure HTTP. Third-party players open only after an explicit tap. Switching tabs closes the external iframe. Starting local playback closes the iframe. Opening an embed pauses local playback.

**Watch separation:** the native Galaxy Watch P.T. Architech app only plays exact allowlisted PTIV tracks from approved sources. Your personal phone offline audio and saved third-party embed links are NOT automatically transferred to the watch. The distinct Android companion accepts only catalog-matching PTIV audio and LRC with original-source checksums.

## Verification on YOG Chrome (isolated profile, 2026-11-13)

CDP headless browser on a temporary local HTTP server, fresh browser profile:
- Dated catalog loaded: **143 entries** (131 published, 12 unreleased Ghost in the Wire).
- Safe provider parses: **6 of 6** (YouTube, Spotify, ordinary and official-iframe SoundCloud, Apple Music, Bandcamp).
- Rejected malicious/unsupported inputs: **6 of 6**.
- Embedded Players tab shown: PASS.
- Saved embed persisted in localStorage without authentication: PASS.
- Personal offline panel visible: PASS.
- Generated original 2.2-second test tone imported as MP3 into IndexedDB: PASS.
- Test file retrieved from IndexedDB and selected: PASS.
- Real audio playback observed in Chrome: currentTime > 0, paused=false: PASS.
- No PTIV source audio or lyric was modified during test.

No claim about real-world provider playback accessibility without opening the corresponding provider iframe on the target phone. External providers control playback availability, cookies and subscription requirements.

## User tutorial

1. On Samsung Chrome / iPhone Safari, open https://flowjuggle.github.io/ptiv-walkman-offline/
2. **Personal offline:** tap **All my music**, then **Add my own offline music**, select a track/folder/ZIP. Tap it to play; check offline in Airplane Mode.
3. **Third-party link:** tap **Embedded players** → paste an official URL / iframe share code → save. Tap **Open**; provider rules apply.
4. **Stop** closes the iframe. Switching back to music also closes it.
5. **P.T. Architech catalog** continues to list published releases and unreleased Ghost separately; attributes with no verified source remain unknown.

All source files, Workbench R3 receipts, and distributor metadata remain unchanged.
