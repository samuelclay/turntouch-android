# Play Console disclosure worksheet — Android 1.0

This is a preparation worksheet, not a claim that Console forms have been submitted.

- App: free smart-home remote companion; House & Home; no ads or billing.
- Privacy policy: https://turntouch.com/static/android/privacy.html
- No app account creation or Turn Touch login. Some integrations require credentials belonging to a third-party device/service.
- Intended audience: adult owners configuring their smart-home equipment; not designed for children. Do not imply a children/families program enrollment.
- No financial, health, medical, government, news-publishing, or gambling functionality. The News mode is a general web viewer, not a news-publishing service.
- No Advertising ID permission or advertising SDK.
- No analytics or crash-report upload SDK. AndroidX/Compose, CameraX, Kotlin coroutines only.

## Data handling evidence

`SecretStore.kt` stores connection credentials using AES-GCM with an Android Keystore key; `Models.kt` stores assignments and preferences locally. `BluetoothController.kt` reads remote identity/battery/button events locally. `NativeScreens.kt` saves camera photos and silent video to MediaStore without uploading them. No GPS location API is used; fine location permission is limited to older Android Bluetooth scanning.

`Integrations.kt`, `Klap.kt`, and discovery helpers send user-configured commands to local hardware. Optional Govee cloud requests include device identifiers and an API key. Optional IFTTT/custom HTTP requests include user-entered values. Kasa KLAP authenticates with derived account credentials. No copy is sent to Turn Touch. User-opened general web pages have their own third-party data handling.

Do not equate “no Turn Touch backend” with “no off-device collection.” The Data safety form must account for direct cloud integration requests as well. Relevant optional categories to assess in the actual form include Device or other IDs (Govee/device configuration), User IDs/account authentication, App interactions (configured actions sent to services), and Other user-generated content (custom webhook values). No ephemeral-only claim is made about external providers' retention. User-initiated transfers may qualify for Google's sharing exception; that is separate from collection.

Do not claim all data is encrypted in transit: local device protocols and user-specified HTTP endpoints can be unencrypted. No app account deletion URL is needed because the app creates no account; users can clear local storage and use providers' own deletion controls for data sent to those services. The public privacy policy explains this distinction.

Reference reviewed: https://support.google.com/googleplay/android-developer/answer/10787469

## Foreground service

Manifest declares `FOREGROUND_SERVICE_CONNECTED_DEVICE`. `RemoteService` maintains a BLE link to a user-paired physical Turn Touch remote so button presses can control configured devices while the activity is backgrounded. It shows a foreground notification and stops when no saved/active remotes remain. Interrupting the service prevents physical remote commands until reconnection. Select the connected-device/external-device interaction purpose in Console. A demonstration video may be required by the live form; do not substitute screenshots for video or claim a video has been uploaded.

## Review access

No Turn Touch account is required. Choose **Try offline demo** in the first-launch pairing dialog, or Settings → Help → Try offline demo. The labeled preview uses sample Hue, Nanoleaf, Govee, and Sonos assignments; screen taps simulate actions, and **Edit buttons** opens the actual action editor. All assignments and connection settings in the demo are held only in memory. Commands are intercepted before network, media, camera, or alarm execution. Exit demo returns to the normal app without changing personal configuration.

The demo is an interface and interaction preview, not hardware proof. Actual BLE pairing and smart-home control still require a physical remote and compatible devices/provider access. Review instructions must describe that limitation honestly.
