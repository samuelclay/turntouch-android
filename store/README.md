# Turn Touch Android 1.1 store assets

The 21 app languages each have seven genuine Samsung phone screenshots, seven Samsung tablet screenshots, and a localized 1024 × 500 feature graphic. The app icon is language-neutral and inherited from the English listing. Screenshots show the current release's live UI with a connected Turn Touch remote; no device status or app text was painted into an image.

- `localization/store-locales.json` maps app language codes to Play locale IDs.
- `localization/store/<language>.json` contains translated listing copy and release notes.
- `store/<locale>/images/phoneScreenshots`: 1080 × 1920 captures.
- `store/<locale>/images/tenInchScreenshots`: 1920 × 1200 tablet captures.
- `store/<locale>/images/featureGraphic.png`: native text rendering with the existing app artwork.
- `manifest.json`: dimensions and SHA-256 checksums.

## Refresh

Build the debug app and instrumentation APK, then run `python3 scripts/capture_store.py --languages all`. The script uses the attached Samsung phone and tablet, requires a connected remote, preserves app assignments, and restores display/demo settings. `--resume` skips complete seven-image sets; omit it for a fresh capture. Update the device IDs when capturing on another machine.

Run `swift scripts/store_graphics.swift` on macOS to regenerate all localized feature graphics. Captions come from the normal incremental translation pipeline; images do not call a translation service. Review each locale, especially Arabic/Hebrew direction and long labels, before uploading.

Upload with locale and device prefixes in filenames so the Play asset library stays unambiguous. Select screenshots in `01`–`07` order after upload; the asset library's automatic selection can follow upload completion order instead of filename order.

Store assets and build uploads are separate from review submission and public availability. Release status is recorded after verifying Play Console.

Run `python3 scripts/index_store.py` after changing assets to refresh the [gallery](localized-gallery.html) and SHA-256 manifest (uses macOS `sips`).
