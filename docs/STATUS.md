# Implementation and testing status

This is the initial Android implementation, with 22 catalog entries derived from the iOS and Mac apps. A catalog entry does not mean that every corresponding feature is complete. Full iOS/Mac parity and exhaustive hardware validation are still in progress.

## Verified on physical devices

The Samsung Galaxy S22 (Android API 36) and Galaxy Tab A8 (API 34) have been used for development:

- Build, install, and launch on both form factors.
- Live local-network discovery of Hue, Sonos, and Nanoleaf.
- Hue bridge pairing on the tablet and Nanoleaf pairing on the phone.
- Loading Hue rooms/scenes and Nanoleaf effects, selecting them inline, and verifying persisted selections.
- Hue Random: independent single/double-tap options, all 27 setting combinations tested, plus real color/saturation/brightness command read-back and state restoration on one reachable room light.
- Whole-diamond touch targets, pressed fill, centered long labels, and visible inline action selection, including preserving other actions in a batch.

The automatic pairing retry loop has tests for eventual success, cancellation, and ignoring late results after cancellation. A new physical pairing round trip with the automatic retry UI still needs to be checked.

## Integration coverage

| Integration | Current implementation | Remaining validation or work |
| --- | --- | --- |
| Hue | Local v1 API pairing, rooms/scenes, brightness, saturation, color, sleep, scene cycling | Full action read-back/restore tests; match all iOS v2 preset/default behavior |
| Nanoleaf | Local pairing, effect list and selection, on/off, brightness | Full action read-back tests; sleep/fade parity |
| Sonos | Discovery and local SOAP media/volume control | Live command validation and group coordinator behavior |
| Kasa | Legacy TCP and KLAP support, UDP discovery | Live authentication and control with available devices |
| Govee | LAN discovery/control and cloud API path | Live device/API validation |
| WeMo / Bose | Local protocol implementations | Hardware unavailable during development; not live-tested |
| Phone / Music | Android volume and active media session controls | Full action/device matrix |
| Spotify / Airfoil | Active Android media session and volume fallback | Dedicated service/device targeting is not implemented |
| Camera | CameraX photo/video screen | Full phone/tablet and remote-trigger coverage |
| Web / News / Video | WebView-based content screens and remote controls | Complete action parity and runtime checks |
| Presentation | Browser-based content | Native PDF presentation support is not yet implemented on Android |
| Yoga | Basic pose/timer screen | Original iOS routine and artwork still need to be ported |
| Custom / IFTTT | HTTP and webhook execution | Live integration tests and complete option parity |
| Alarm Clock | Android alarm intents | Full equivalent action behavior |
| HomeKit catalog entry | No working Android connection | Native Google Home integration needs SDK and authorization setup; Home Assistant is planned separately |
| Nest | No working connection | Google Device Access/OAuth setup and implementation |

## Bluetooth

The app implements scanning, GATT notifications, button decoding, four-button confirmation, nickname/battery reads, saved connections, and foreground listening. The decoder has tests for single/double/held presses, chords, malformed packets, and reset behavior.

A complete live Turn Touch four-button test and background reconnect endurance run have not yet passed. Sol page turners share a service identifier and are excluded by their advertised names. Firmware updating is not implemented.

## Testing

- `ButtonDecoderTest`, `NetworkTest`, `PairingTest`, and `HueRandomTest` run locally without hardware.
- `DiamondInteractionTest` verifies hit targets, highlight shape, centered long labels, and action chooser visibility on a device. Temporary button changes are restored.
- `InlineLightingOptionsTest` requires real saved pairing credentials and a Hue/Nanoleaf mode slot. Run the appropriate method on the device paired with that integration. It reads device catalogs and changes/restores button assignments; it does not activate an effect or scene.
- `HueRandomOptionsTest` verifies the six controls in 16-button mode and independent per-tap options in 12-button mode. It restores the original button preferences.
- `HueRandomLiveTest` requires saved Hue credentials. It briefly changes a small reachable color-light room, checks the result, and restores the original light states.
- `DeviceSmokeTest` uses Bluetooth scanning and local Wi-Fi discovery, and may attempt a connection to a discovered Turn Touch.
- `LiveIntegrationTest` is an opt-in protocol test that requires a private `files/test-connections.json` fixture. It changes one light's brightness and a speaker's volume briefly, then restores them. Do not commit credentials or device fixtures. Its current combined Hue/Sonos test needs to be split so each integration can be checked independently.

Raw device logs and local evidence are intentionally ignored by Git because they can contain network addresses, remote identifiers, or test configuration. README screenshots are curated separately.
