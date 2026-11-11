# Xtra5 (custom build of Xtra)

Personal fork of official Xtra (crackededed/Xtra) with custom features. Work happens on the
`custom-xtra5` branch (the default branch). `master` mirrors official Xtra and is never pushed to.

## Working rules

- **Always confirm the plan first.** Before writing or changing any code, describe the planned
  changes (what, where, and behaviour) and wait for the owner's explicit go-ahead.
- Once a new version is built (and the plan was approved), publish it right away: merge it into
  `custom-xtra5` (which publishes the release) without waiting for the owner to test. Also send
  the APK and `xtra5-Custom-Changelog.txt`.
- Never open pull requests to, or push to, the official Xtra repository.

## Keeping upstream merges clean

- Put custom logic in `app/src/main/java/com/github/andreyasadchy/xtra/custom/`.
- Keep edits to upstream files to small, clearly marked hooks (`// Xtra5 custom`) or blocks
  appended at the end of a file. Custom strings go in `res/values/xtra5_strings.xml`.
- Commits must be authored as `prime385`: the build uses the newest non-prime385 commit to know
  which official Xtra version it is based on.

## Versioning and releases

- Version is `MAJOR.FEATURE.FIX`, set by `val xtra5Version` at the end of `app/build.gradle.kts`
  (versionCode = MAJOR*10000 + FEATURE*100 + FIX). New features bump FEATURE.
- Every new version needs an entry at the top of `XTRA5_CHANGELOG.txt` (same format as the
  existing entries: a `========================================` separator line, then
  `Xtra5 X.Y.Z (date)`, `Based on official Xtra <sha>`, then New / Changed / Fixed /
  Behind the scenes sections). Send the full history as `xtra5-Custom-Changelog.txt` (always this exact name, so the owner
  can save over the same local file) alongside every APK sent to the owner.
- `.github/workflows/xtra5.yml` merges official Xtra daily, bumps FIX, builds and publishes a
  release with the APK and `xtra5-Custom-Changelog.txt` (full history; the release description is
  that version's entry; it writes the entry itself for automatic builds);
  the in-app updater installs releases from this repo.
