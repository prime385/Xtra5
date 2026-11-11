# Xtra5 (custom build of Xtra)

Personal fork of official Xtra (crackededed/Xtra) with custom features. Work happens on the
`custom-xtra5` branch (the default branch). `master` mirrors official Xtra and is never pushed to.

## Working rules

- **Always confirm the plan first.** Before writing or changing any code, describe the planned
  changes (what, where, and behaviour) and wait for the owner's explicit go-ahead.
- Test builds go on a `wip-*` branch and are sent as an APK for testing. Only merge into
  `custom-xtra5` (which publishes a release) after the owner confirms the build works.
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
  existing entries: `Xtra5 X.Y.Z (date)`, `Based on official Xtra <sha>`, then New / Changed /
  Fixed sections). Send `Xtra5-X.Y.Z-changelog.txt` (that entry) alongside every test APK.
- `.github/workflows/xtra5.yml` merges official Xtra daily, bumps FIX, builds and publishes a
  release with the APK and its changelog .txt (it writes the entry itself for automatic builds);
  the in-app updater installs releases from this repo.
