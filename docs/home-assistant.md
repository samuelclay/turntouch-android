# Home Assistant

The native Home Assistant editor matches the Mac and iOS flow: search, category filter, entity list, collapsed **Action settings** and optional JSON, **Test action**, then a separate blue **Set remote button** with the destination and confirmation. It is available in the action chooser and Settings → Apps & Connections. Settings browsing does not assign a button.

The ellipsis menu contains connection, sign-in, access-token entry, refresh, token settings, and disconnect. Local homes are discovered over `_home-assistant._tcp.`. Sign-in stays on Home Assistant's own page; the app receives an OAuth code. OAuth tokens are encrypted using the existing Android Keystore-backed SecretStore. REST loads entities and services; optional WebSocket registry metadata enables room/device searches. No Home Assistant plugin is required.

The binding schema matches Mac/iOS (`entity_id`, `service`, `data`, `name`, `server_url`). Both 12- and 16-button editors and the normal remote dispatcher use it. Test does not assign, and assignment does not execute. Target overrides in JSON are rejected, required data is validated, authenticated redirects are refused, and bindings cannot silently control a different home. Automation conditions are checked by default.

## Validation

Build with `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`.

Start `python3 scripts/home_assistant_fixture.py` on the development Mac. For each physical Android device:

```sh
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL reverse tcp:18123 tcp:18123
adb -s SERIAL shell am instrument -w -r -e class com.turntouch.android.HomeAssistantFlowTest com.turntouch.android.test/androidx.test.runner.AndroidJUnitRunner
```

Tests use a separate encrypted credential namespace and restore the user's assignments/layout. They verify search, test-versus-save behavior, persistence through a new AppState, normal action dispatch, automation conditions, and cross-home rejection in both button layouts. They also check OAuth credential restoration and capture German and Arabic layouts. Screenshots are saved to the app's external files directory. The fixture never controls physical devices; live-home authentication and a physical remote press are separate checks.
