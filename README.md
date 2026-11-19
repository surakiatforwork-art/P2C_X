# P2C_X

<img src="art/p2cx-icon-source.png" width="128" alt="P2C_X ghost gallery icon">

An LSPosed/Xposed module for testing Android camera capture flows with user-selected images. Includes an image-only gallery and a minimal crop, mirror and resize editor.

## Features

- Replace activity-based camera intents and supported CameraX file captures with image selection.
- Tap a thumbnail to open its image in the editor immediately.
- Original aspect ratio by default; free, 1:1, 4:3 and 16:9 crop presets.
- Horizontal/vertical mirror, output dimensions and aspect lock.
- Remember confirmed editor settings; cancelling does not save changes.
- Album selection, lazy thumbnails and Android 14+ partial image access.
- Light/dark themes following the system and adaptive launcher artwork.
- Persistent module switch, Quick Settings tile and optional enable-on-boot.

## Requirements and installation

1. Android 8.0 or newer, with an LSPosed-compatible environment supporting Xposed API 93 or newer.
2. Install the APK from [Releases](https://github.com/surakiatforwork-art/P2C_X/releases).
3. Enable P2C_X in LSPosed and select the target apps in its scope.
4. Open P2C_X once and grant image access through Gallery. The module and enable-on-boot switches default to on for new settings; existing switch choices survive updates.
5. Fully close and reopen each target app after installing/updating the module or changing scope. Existing target processes keep their loaded module code and may retain pending capture requests.
6. Enter the target camera, press the shutter, choose an image, adjust it and confirm.

A Quick Settings tile can be added for switching the module on/off. Initial setup is still required. Force-stopping P2C_X prevents boot broadcasts until it is opened again.

## Compatibility

| Camera path | Status |
| --- | --- |
| Activity-based `ACTION_IMAGE_CAPTURE` | Gallery/editor result returned through the original activity result contract |
| CameraX file, MediaStore and output-stream capture | JPEG written and capture callback delivered |
| Obfuscated CameraX | DEX marker discovery and reflected method/callback inspection |
| Optimized callback layouts | Adapters checked against GhostShift 1.29 and Nera 2691.1.0 |
| CameraX in-memory capture/analysis | Not implemented |
| Direct Camera2, legacy Camera, native camera and live video | Not implemented |

Compatibility depends on the camera implementation and optimizer settings. This release does not claim support for every app. Only apply the module to apps and test environments you are authorized to test.

## Editor and image handling

Original aspect ratio follows the current source image. Confirmed crop coordinates are normalized for reuse with different image dimensions. Reset restores the original ratio, full frame, original size and no mirror; confirm to save the reset settings.

Output is JPEG, with a maximum of 8192 pixels per side and 24 megapixels. Source decoding also has a 24-megapixel limit. The editor does not overwrite the original image.

Image selection/editing runs in P2C_X. A read-only URI grant gives the target access to the rendered cache file, and the target writes its own capture output. Settings use LSPosed's shared-preferences transport.

## Build

Use JDK 17 and Android SDK 35. The wrapper uses Gradle 8.9 and Android Gradle Plugin 8.7.2. Xposed API is a compile-only dependency from its official Maven repository.

Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Linux/macOS:

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

The current prerelease is a debug-signed test build. Build and sign with your own release key for a separate production distribution.

## Verification

`ContractChecks` checks editor state, mirrored crop rendering, output dimensions, provider access, invalid sizes, single-tap gallery navigation and UI reset. It also loads the three sample apps' installed APKs in isolated class loaders to verify capture signatures, DEX discovery, file writes and callbacks.

The sample APKs are not distributed here. Install your authorized copies of Image Keeper (`com.branchphotovault`), GhostShift (`com.phantom.ghostshift`) and Nera (`com.mca.neraapp`) before running the complete contract suite. The gallery UI check requires full image permission and at least one local image.

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.phantom.p2cx.test/com.phantom.p2cx.ContractChecks
```

For v0.5.0-test, build/lint and the on-device contract/UI suite passed on the connected Android 15 test device. Image Keeper also logged successful image delivery after its process was restarted. Contract tests do not replace full camera UI tests for each target app; real reboot behavior remains to be verified.

## Contributing

Include Android/LSPosed versions, the camera library/version, capture signature, a reproducible flow and relevant `P2C_X` logs. Cover optimized callback adapters with a contract check. Do not include private photos, credentials or third-party APKs in reports or pull requests.

## Credits and license

Pict2Cam by Adrian Garcia inspired this project: [adriangl/pict2cam](https://github.com/adriangl/pict2cam). P2C_X uses a separate Java/Xposed implementation. This repository contains the P2C_X module only; upstream Pict2Cam source is not included.

P2C_X launcher artwork was supplied by the project owner. See [art](art/README.md).

Licensed under [Apache-2.0](LICENSE).
