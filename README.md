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
