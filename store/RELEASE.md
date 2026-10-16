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

Review access instructions: Open the app and use the pairing dialog's cancel/close control to explore configuration without hardware. Settings > How it works opens the walkthrough. Settings > Apps & connections configures third-party hardware. To test physical control, use a Turn Touch BLE remote and compatible smart-home devices on the same local network. A remote pairs with one host at a time. No universal review credentials or simulated hardware are provided.

## Validation

- 17 JVM tests passed; release lint has no errors (460 existing warnings).
- Dedicated upload signature verified; release APK ZIP alignment and all 64-bit ELF LOAD segments meet 16 KB alignment.
- Detailed integration coverage and known gaps remain documented in `../docs/RELEASE_VALIDATION_2026-10-16.md` and `../docs/STATUS.md`.
- This file records preparation, not confirmation of Play submission or publication. Console status must be verified separately.
