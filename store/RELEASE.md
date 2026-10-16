# Turn Touch Android 1.0

Package: `com.turntouch.android`; version name: `1.0`; version code: `1`.
Target SDK 36, minimum SDK 26. Free app, no ads or in-app purchases.
Developer account selected by the owner: NewsBlur, Inc. (6280481402178293168), accessed through conesus@gmail.com.

## Build and signing

```
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:lintRelease :app:bundleRelease :app:assembleRelease
```

The upload key is stored outside Git in `~/.config/turntouch/android-upload.jks`.
Signing configuration is in `~/.config/turntouch/android-release.properties`, with owner-only filesystem permissions. `TURNTOUCH_SIGNING_PROPERTIES` can point at an alternative properties file. Back up both files to the owner's secure credential storage before replacing this computer. Never commit their contents.

Upload certificate SHA-256: `b28611a43a9dad40efb8721a6d0ca26876cb44a764abc319433f7f11dbd9cb34`.
Play App Signing should generate and manage the distribution key; this local key is for uploads.

Bundle: `app/build/outputs/bundle/release/app-release.aab`.
The debug APK uses the same version/source and the existing debug certificate so physical-device settings survive installation. It is not the Play distribution artifact.

## Store listing

English metadata and assets are in `en-US/`. Category: House & Home.
Support: samuel@turntouch.com. Website: https://turntouch.com.
Privacy: https://turntouch.com/static/android/privacy.html (also available offline in Settings).
No Turn Touch login is required. Physical remote use requires separately purchased Turn Touch hardware. Individual integrations require compatible devices and sometimes their own credentials/API keys.

Review access instructions: Choose **Try offline demo** in the first-launch pairing dialog, or Settings → Help → Try offline demo. The labeled preview uses sample devices and intercepts all commands; **Edit buttons** opens the real action editor with temporary in-memory assignments. Exit demo returns to personal configuration unchanged. Real physical control requires a Turn Touch BLE remote and compatible smart-home devices/provider credentials. A remote pairs with one host at a time.

## Validation

- 17 JVM tests passed; release lint has no errors (460 existing warnings).
- Dedicated upload signature verified; release APK ZIP alignment and all 64-bit ELF LOAD segments meet 16 KB alignment.
- Detailed integration coverage and known gaps remain documented in `../docs/RELEASE_VALIDATION_2026-10-16.md` and `../docs/STATUS.md`.
- This file records preparation, not confirmation of Play submission or publication. Console status must be verified separately.

## Fresh screenshot pass

Seven screenshots per device were captured on October 16, 2026 using source `5aadbb7`. Both capture runs wait for a genuine BLE Connected state and restore original button preferences. The installed debug APK on both Samsungs was checked byte-for-byte against the final local artifact and reports version 1.0.

S22 screenshots use a temporary 1080×1920 display size for Play's aspect-ratio limits; native 1080×2340 was restored. Tab A8 captures use its native 1920×1200 landscape layout. System demo mode hides notifications during capture and is restored afterward; actual clock/battery values remain visible on Samsung. These are app screenshots, with no synthetic connection status or composited app UI.

Repeat after building debug app/test APKs: `python3 scripts/capture_store.py` (uses the two attached device serials and temporarily stops the other remote listener). Browse `index.html` for the complete gallery. `scripts/store_graphics.swift` renders the separate feature graphic from existing Turn Touch artwork.

Play app created under NewsBlur, Inc.: app ID `4974725381826331297`.

## Offline review demo

The offline demo is available in the first-launch pairing dialog and Settings → Help. It reuses the app UI and action editor with in-memory assignments and simulated controls. Two instrumentation checks passed on each physical Samsung device: UI editing/execution/exit/reset with personal preferences unchanged, and interception of network/native action routes. Fresh demo captures are in ignored `evidence/play-release-1.0/`. Unit tests and release lint/build passed after this addition. The signed artifact manifest was refreshed after rebuilding source commit `b57f826`.

## Console preparation status

All initial setup tasks were saved on October 16, 2026. The default English listing is ready to send for review, with seven phone and seven 10-inch tablet screenshots, icon, and feature graphic. Category/contact details and target audience (18+, without restricting minor users) are saved.

Data safety discloses optional collection of User IDs, App interactions, Other user-generated content, and Device or other IDs for app functionality. No ephemeral-processing claim is made. User-initiated provider transfers use the sharing exemption. The form does not claim universal transit encryption or a developer-operated deletion mechanism; the privacy policy explains local clearing and provider controls. External provider accounts are described explicitly; Turn Touch creates no account.

The production release has not yet been uploaded or submitted. Country selection is in progress. The verified upload bundle is `evidence/play-release-1.0/TurnTouch-1.0.aab`, SHA-256 `055695317dfcb630a622d0a39023a920be66a4a2f8a51569d5337f5e4d3720a3`.
