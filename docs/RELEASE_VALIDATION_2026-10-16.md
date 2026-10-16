# Release validation — 2026-10-16

This is a physical-device validation of the working tree based on `c3bbc1934c898a6eb7ef3c19fea609c8459cf2ee`, with the changes described below. It is not a Play Store release or a claim of complete integration parity.

## Devices and build

- Samsung Galaxy S22 (SM-S901U1), Android API 36.
- Samsung Galaxy Tab A8 (SM-X200), Android API 34.
- Both initial installed APKs exactly matched the initial local debug artifact by SHA-256.
- Updated debug builds were installed without clearing app data. Both final installed APKs match the built SHA-256 `3e075d3c15c581e5f9c0e17bf70133ac09d75fcae732bfa7b026c7a2bcce01f2`. Private preference backups and raw diagnostics are under ignored `evidence/release-validation/`.
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
| Bluetooth off | Passed | Passed | Adapter off/on recovery while the activity stays backgrounded; original S22 check also covered the actionable pairing failure screen |
| Govee LAN | Not found | Not found | No saved credentials and no discovery response; not a hardware pass |
| Kasa LAN | Not found | Not found | No saved configuration and no discovery response; not a hardware pass |

The Hue brightness test allows one unit of read-back rounding: a requested increment from 100 by 25 reported 124 on this bridge, and the reverse step returned 100. Other action assertions remain exact.

## Remaining release gaps

- Physical remote → screen-off S22 → Nanoleaf single-tap control passed with state read-back (13:41:35, then repeated 13:42:06–13:42:36). The full three-gesture assertion remains incomplete: the first S22 trial timed out with Single only, and the second was intentionally cancelled through its error/cleanup path when the user asked to test their normal Hue assignments. Double-tap and hold were not proven end-to-end in this controlled scenario. Earlier tablet trials received no events. All trials restored light state and assignments; no temporary loopback bindings remain.
- Long screen-off/overnight endurance remains unverified. The automated background test checks short retention and real adapter-off/on recovery; it does not simulate hours of Doze or vendor battery restrictions.
- Govee needs an accessible LAN-enabled device or configured cloud API credentials. Kasa needs an accessible device and, for KLAP, account authorization.
- Hue Early evening and other named presets currently require selecting a scene in Android; automatic iOS default scene creation is not implemented. A real double tap exposed the missing scene setting.
- Sonos transport/group behavior, Nanoleaf sleep/fade parity, Govee cloud, Kasa authentication/control, and unavailable Bose/WeMo hardware remain unverified.
- Camera, browser/video, alarm, IFTTT and other catalog features are not covered by the lighting checks. Google Home and Nest are not implemented connections; see `STATUS.md`.

## Background connection investigation

The tablet held the desk remote while the S22 was also trying to connect. The S22 logged repeated GATT 147 failures from 13:29:56 through 13:32:57; after stopping the tablet listener, the S22 enabled button notifications at 13:33:18. Closing an activity does not stop its foreground listener. Test one host at a time for the same remote. The tablet service was still running when inspected; these logs do not establish Samsung killing it in the background.

Saved remotes previously used direct connection attempts (`autoConnect=false`) with a 15-second timeout and delayed retries. Opening the app still makes a fast direct attempt; background recovery now uses Android's persistent automatic connection, retains its registration on normal link loss, and recreates failed registrations after stack errors. Initial four-button pairing retains its bounded direct connection. Service discovery/subscription failures are bounded and retried. The foreground service now observes Bluetooth adapter changes, clears obsolete connections when the radio turns off, and reconnects saved remotes when it returns without needing to reopen the activity.

`BackgroundConnectionTest` is opt-in with `-e backgroundBluetooth true`. It requires a nearby saved remote and no competing host, backgrounds the activity for 20 seconds, disables/enables Bluetooth, asserts that the app clears and reestablishes its connection, and checks another 20 seconds of background retention. It always re-enables Bluetooth afterward. Both Samsungs passed on the final build, tested sequentially: the tablet in 47 seconds and the S22 with its activity continuously backgrounded across the Bluetooth toggle. An earlier automatic-only initial connection attempt timed out on the tablet; keeping a direct foreground attempt restored prompt connection. This checks a live connection/subscription, not physical-button action execution or overnight endurance.

With the original S22 assignments restored and its screen off, physical Up presses at 13:45:23, 13:45:26 and 13:45:28 dispatched Hue Random successfully (bridge accepted every command) and Nanoleaf Turn On successfully. Govee alone reported a missing API key. This confirms real BLE-triggered dispatch using the user's normal assignments; the normal Hue run was not separately read back or visually confirmed in this session. The preceding controlled Nanoleaf single-tap checks did read actual state back.

The tablet was reopened during testing and took the BLE connection again at 13:43:55 while the S22 instrumentation process restarted. Stopping the tablet restored the S22 connection at 13:44:45. Avoid running both listeners against the same remote. The tablet's original Up binding is Early Evening with no selected scene, which remains a separate known preset implementation gap; the S22's original Up batch is Hue Random, Nanoleaf Turn On, and Govee Turn On.

## Repeatable checks

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

`ReleaseHardwareTest` reads saved encrypted configurations and discovers local hardware. Hardware absence is reported as a JUnit assumption/skip, never a pass. `RemoteLightLiveTest` requires `-e liveRemote true` and a person ready to single-tap, double-tap, and hold; it temporarily assigns Nanoleaf actions, backgrounds the app, verifies state after actual BLE-triggered actions through a loopback confirmation endpoint, and restores lights and assignments. The timeout test in `PairingFlowTest` requires `-e pairingTimeout true` and no physical button presses.

## Final device state

Bluetooth is enabled on both devices. Temporary test bindings were removed and tested light/speaker states restored. After the background investigation, the S22 is the intended sole listener; the tablet listener is stopped to avoid competing for the same remote. No store submission or release publication was performed.
