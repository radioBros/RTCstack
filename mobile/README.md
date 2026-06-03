# RTCstack Mobile (iOS + Android)

Native SDKs and UI kits that mirror the shipped `@rtcstack/sdk` contract, wrapping LiveKit's
native clients the same way the web SDK wraps `livekit-client`, and building the UI kits on
LiveKit's official Components libraries.

> **Status:** both SDKs compile against their pinned dependencies and the cross-platform
> wire-format conformance tests pass (Android 7/7, iOS 6/6). Device-level features
> (CallKit/VoIP, ReplayKit/MediaProjection screen share, audio routing, incoming-call push)
> are implemented but require a real-device pass and your own app/push credentials to exercise.

## Layout

```
mobile/
├─ android/                ← Gradle multi-module project
│  ├─ sdk/                 com.rtcstack:sdk      (wraps io.livekit:livekit-android)
│  ├─ ui-compose/          com.rtcstack:ui-compose (Compose, on components-android)
│  └─ example/             join-form → VideoConference demo app
└─ ios/                    ← Swift Package
   ├─ Sources/RTCstackKit/ wraps client-sdk-swift (incl. Platform/ CallKit·VoIP·AudioSession·ReplayKit)
   ├─ Sources/RTCstackUI/  SwiftUI, on components-swift
   ├─ Tests/               wire-format interop tests
   ├─ Example/             SwiftUI demo app sources (create the Xcode app target on Mac)
   └─ BroadcastExtension/  ReplayKit screen-share extension template
```

## Build & test

```bash
# Android (requires JDK 17 + Android SDK; Gradle wrapper is committed)
cd android && ./gradlew :sdk:assembleRelease :ui-compose:assembleRelease \
                        :sdk:testReleaseUnitTest :example:assembleDebug

# iOS (requires Xcode 15+/Swift 5.9+)
cd ios && swift test --filter WireFormatTests           # core + wire-format tests
xcodebuild -scheme RTCstackKit -destination 'generic/platform=iOS' build
xcodebuild -scheme RTCstackUI  -destination 'generic/platform=iOS' build
```

Pinned dependencies: Android `io.livekit:livekit-android` **2.24.1** + `livekit-android-compose-components` **2.3.0**; iOS `client-sdk-swift` **2.14.1** + `components-swift` **0.1.7**.

## Design intent

- **Contract source of truth:** the *shipped* `packages/sdk/src/call.ts`, documented authoritatively
  in [`../packages/sdk/WIRE_FORMAT.md`](../packages/sdk/WIRE_FORMAT.md). Native clients interoperate
  with web clients in the same room; the data-channel wire format is the cross-platform commitment,
  enforced by conformance tests on all three platforms (Android 7/7, iOS 6/6).
- **Security:** the SDKs hold only a LiveKit JWT + WSS URL, never the RTCstack API key/secret.
  Mint tokens on your backend (`POST /v1/token`).

## What the integrating app must provide

These are per-app concerns the SDK cannot ship for you:

- **iOS:** an Xcode app target hosting `Example/` (or your own UI); bundle IDs + signing team; an
  **App Group** shared with the ReplayKit Broadcast Extension; an **APNs VoIP** key + PushKit
  entitlement for incoming calls; Info.plist `NSCameraUsageDescription`, `NSMicrophoneUsageDescription`,
  `UIBackgroundModes = [audio, voip]`.
- **Android:** a **Firebase/FCM** project + your `google-services.json` for high-priority
  incoming-call push (see [`android/incoming-call-example/`](android/incoming-call-example/)).
- **Both:** a backend that mints tokens (`POST /v1/token`) and a real-device QA pass — device
  features (CallKit/VoIP, screen share, audio routing) can only be validated on hardware.
