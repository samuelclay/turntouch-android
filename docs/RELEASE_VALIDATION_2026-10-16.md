# Release validation — 2026-10-16

This is a physical-device validation of the working tree based on `c3bbc1934c898a6eb7ef3c19fea609c8459cf2ee`, with the changes described below. It is not a Play Store release or a claim of complete integration parity.

## Devices and build

- Samsung Galaxy S22 (SM-S901U1), Android API 36.
- Samsung Galaxy Tab A8 (SM-X200), Android API 34.
- Both initial installed APKs exactly matched the initial local debug artifact by SHA-256.
- Updated debug builds were installed without clearing app data. Both final installed APKs match the built SHA-256 `2aa5b60b8d024d786129ad72ce1774e808b55966ab049a71983cfaf2c3064693`. Private preference backups and raw diagnostics are under ignored `evidence/release-validation/`.
- The unsigned release APK also built successfully with its release vital-lint checks; it was not installed, signed for distribution, or uploaded.
- 17 JVM tests passed. Android lint initially found an API-27 navigation-bar attribute in an API-26 theme; moving it to `values-v27` removed the error. Existing warnings remain.

## Fixes and requested UI changes

- Ported the iOS pairing form sheet using original hero/success/failure assets, Effra typography, colors, 100dp footer, pulsing scan indicator, and a four-button confirmation diamond.
- Added automatic discovery/connection, a 20-second search deadline, a 15-second connection deadline, and a 30-second button-confirmation deadline; retry and cancellation dispose of unconfirmed connections.
- Added a five-page swipeable tutorial with page dots and the original illustrations. Android-specific text describes the actual 12/16-button behavior and configuration; it does not promise the unimplemented iOS HUD.
- Fixed the reproduced cold-launch reconnect failure. Previously a saved remote remained disconnected after reopening the app, with no foreground listener. Saved remotes now appear immediately and reconnect when the activity starts.
- Ignore late GATT callbacks from closed connections; stop the foreground service when no saved or active connections remain.

## Confirmed physical results

| Capability | S22 | Tab A8 | Scope |
| --- | --- | --- | --- |
| BLE discovery and four-button pairing | Passed | Passed | Same desk remote, tested sequentially; tablet used the new modal flow |
| Nickname and battery | Passed | Passed | Remote nickname loaded; battery read 62% |
| Single/double/hold notifications | Received | Pending controlled check | S22 packets included hardware double and hold; actions with missing configuration correctly logged errors |
| Startup reconnect | Observed | Observed | Notifications enabled after fresh process/activity launch |
| Hue Random | Passed | Passed | Real color, saturation, brightness read-back; one small room restored |
| Broader Hue actions | Passed | Passed | Brightness, saturation, hue shifts, color loop, off, immediate sleep, custom and cycling scenes; state restored |
| Nanoleaf | Passed | Passed | On/off/toggle, brightness up/down, effect selection; read-back and restoration |
| Sonos | Passed | Passed | Volume up/down and mute through ActionExecutor; read-back and restoration |
| Android media volume | Passed | Passed | Up/down/mute; original state restored |
| Diamond and options UI | Passed | Passed | Existing hit-target, batch-preservation, and single/double Hue options tests |
| Tutorial, timeout/retry, and pairing cancellation | Passed | Passed | Swipe forward/back, footer navigation, real search/button-confirmation timeouts, retry, close, stop scan, ignore late callbacks |
| Bluetooth off | Passed | Not run | S22 displays the actionable Bluetooth-off failure/retry screen; adapter restored |
| Govee LAN | Not found | Not found | No saved credentials and no discovery response; not a hardware pass |
| Kasa LAN | Not found | Not found | No saved configuration and no discovery response; not a hardware pass |

The Hue brightness test allows one unit of read-back rounding: a requested increment from 100 by 25 reported 124 on this bridge, and the reverse step returned 100. Other action assertions remain exact.

## Remaining release gaps

- The controlled physical remote → background app → Nanoleaf check remains incomplete. The tablet trials (three-minute and ten-minute windows) received no button events and failed their required-gesture assertion. Both cleanup blocks restored the Nanoleaf state and assignments; the final tablet preferences contain no temporary loopback bindings. The equivalent controlled S22 trial has not run.
- Finish a full Bluetooth radio-off/on disconnect/reconnect test. The tablet toggle kept BLE active (its wait-for-OFF command timed out), so that attempt does not establish a radio-disconnect pass; Bluetooth was restored. Long screen-off/overnight endurance is not established by a short session.
- Govee needs an accessible LAN-enabled device or configured cloud API credentials. Kasa needs an accessible device and, for KLAP, account authorization.
- Hue Early evening and other named presets currently require selecting a scene in Android; automatic iOS default scene creation is not implemented. A real double tap exposed the missing scene setting.
- Sonos transport/group behavior, Nanoleaf sleep/fade parity, Govee cloud, Kasa authentication/control, and unavailable Bose/WeMo hardware remain unverified.
- Camera, browser/video, alarm, IFTTT and other catalog features are not covered by the lighting checks. Google Home and Nest are not implemented connections; see `STATUS.md`.

## Repeatable checks

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

`ReleaseHardwareTest` reads saved encrypted configurations and discovers local hardware. Hardware absence is reported as a JUnit assumption/skip, never a pass. `RemoteLightLiveTest` requires `-e liveRemote true` and a person ready to single-tap, double-tap, and hold; it temporarily assigns Nanoleaf actions, backgrounds the app, verifies state after actual BLE-triggered actions through a loopback confirmation endpoint, and restores lights and assignments. The timeout test in `PairingFlowTest` requires `-e pairingTimeout true` and no physical button presses.

## Final device state

Bluetooth is enabled on both devices. Temporary test bindings were removed and tested light/speaker states restored. The tablet app was reopened to reconnect to the desk remote; the S22 app was left closed to avoid competing for its BLE connection. No store submission or release publication was performed.
