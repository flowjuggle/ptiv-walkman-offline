# PTIV Walkman — Offline iPhone player

Live: https://flowjuggle.github.io/ptiv-walkman-offline/

## Lyrics safety contract

- **Words are never AI-generated.** Independently checked lyrics display as VERIFIED; original-source lyric archives may be displayed as **ORIGINAL SOURCE · NOT AUDIO-VERIFIED** while awaiting a released-master review.
- The released master and existing PTIV Lyrics Workbench R3 review records are authoritative; the site does not update Too Lost or Musixmatch.
- Walkman accepts source_manifest.json (album, artist, tracks with title, lyrics, original link) for original-source drafts. Verification-grade JSON additionally requires reviewer, review date, released-master SHA-256, and evidence receipt.
- **The website validates receipt fields, not authenticity.** Full master/word-level review must occur in the existing PTIV workflow before export.
- AI may create **timestamp candidates only**, with the literal verified words unchanged. Imported LRC is refused if any nonempty lyric line differs from the approved text.
- Timestamp drafts are visibly marked *AI TIMING DRAFT* until validated against the released recording. No automatic publishing.
- Old unverified files are not silently promoted. Imported original source texts can be previewed with a conspicuous NOT AUDIO-VERIFIED label. AI timestamps are **blocked** for these records until the master word check is complete.

### JSON record contract

Export a reviewed song from the existing PTIV workflow in the following structure. Bracketed placeholders are documentation only — not sample lyrics or verification claims.

```json
{
  "tracks": [
    {
      "track_title": "<exact audio title>",
      "lyrics": "<reviewed words and line breaks; never AI-generated>",
      "verification": {
        "status": "VERIFIED",
        "released_master_sha256": "<real 64-character master recording sha256>",
        "reviewer": "<name or reviewer identifier>",
        "reviewed_at": "<actual review date>",
        "evidence_ref": "<real receipt or evidence path>"
      }
    }
  ]
}
```

### AI timing contract

The offline AI alignment worker is **not yet installed/integrated into this iPhone app**. Generate a local `.lrc` file using verified words, with a timestamp preceding each **unchanged**, nonempty verified lyric line. Import the result via **Import AI timestamps (LRC)** for the selected track. Walkman validates that the timed line count and exact text match before accepting it. This does **not** constitute timestamp verification.

### Offline operation

The website's UI shell is cached; local audio/lyrics depend on available iOS storage. Import music from iPhone Files in a regular Safari or home-screen app session. No audio is uploaded to this site.

### Ghost in the Wire source archive

The user-supplied archive contains 13 source lyric texts copied from the source manifest; source files and manifest match exactly by track index. The archive README explicitly states they are **not** verified against the audio and includes arrangements, production directions, duplication/artifact risks. The active *Ghost in the Wire* playlist has 12 tracks and excludes `NO SUMER SUN Paper Crown`. The original source manifest still preserves all 13 entries, including the excluded song, for archival integrity. Import it in the app using **Import original lyrics (JSON)**, with no public upload of lyric contents. Original files and released masters remain authoritative. Shazam/Apple Music may expose catalog metadata and `hasLyrics` but cannot automatically supply complete authorized text through the connected catalog search.


## Cross-device v3: Samsung Galaxy, Winamp-inspired visualizer, catalog

- Android phone: open the same HTTPS GitHub Pages URL in Chrome and use **Add to Home screen / Install app**; import music locally on that phone. Catalog and UI are service-worker cached. iPhone remains supported, with its separate local song storage.
- PTIV Catalog tab: 131 entries from the dated October 12, 2026 Too Lost export + 12 unreleased Ghost in the Wire draft titles. The catalog is a **metadata index**, not a public music download. Only locally imported audio is playable offline. This list may need refreshing from a newer authoritative Too Lost export.
- Catalog filters: release, publication status, BPM range, language, mood, style, sorting and text search. Local editable tags for missing values; saved smart playlists and play/shuffle matching imported audio. **Unknown attributes are not invented**. Saved local tags do not change Too Lost.
- Cassette now has five screens. The Winamp-inspired visualizer is a **real Web Audio analyser** after the user taps Enable, using locally played audio. Spectral bars, waveform and fire palette are visual options. It never captures microphone audio or sends audio to a remote server. Audio analysis is opt-in to avoid disruptions or battery load.
- Android Media Session allows supported Galaxy/Android system media controls and compatible Bluetooth/Galaxy Watch phone-remote controls. Support depends on browser, device and OS; a paired watch cannot automatically access audio saved only on the phone.
- **Separate standalone Wear OS experiment** lives in [wear/](wear/README.md); builds an APK that scans on-watch audio, plays offline, supports shuffle/continuous mode and optional locally imported LRC lyrics. This native watch package is **not watch-device verified** and needs a physical install and music transfer test.
- The iPhone player and its IndexedDB records were not reset or migrated. Original recordings and verified lyric receipts are never published in the repo.

To build the watch test APK, run the GitHub Actions manual workflow **Build PTIV Watch APK (test only)**. See [wear README](wear/README.md) for hardware acceptance checks.
