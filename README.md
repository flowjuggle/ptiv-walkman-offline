# PTIV Walkman — Offline iPhone player

Live: https://flowjuggle.github.io/ptiv-walkman-offline/

## Lyrics safety contract

- **Only verified original lyrics may be displayed**. Walkman does not invent or transcribe lyrics.
- The released master and existing PTIV Lyrics Workbench R3 review records are authoritative; the site does not update Too Lost or Musixmatch.
- A JSON import must include each song's exact verified lyric text and its review evidence (reviewer, reviewed date, released-master SHA-256, receipt reference).
- **The website validates receipt fields, not authenticity.** Full master/word-level review must occur in the existing PTIV workflow before export.
- AI may create **timestamp candidates only**, with the literal verified words unchanged. Imported LRC is refused if any nonempty lyric line differs from the approved text.
- Timestamp drafts are visibly marked *AI TIMING DRAFT* until validated against the released recording. No automatic publishing.
- Previous unverified lyric imports are **not displayed**.

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
