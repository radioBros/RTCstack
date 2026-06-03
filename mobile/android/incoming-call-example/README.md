# Android incoming-call wiring (reference, not part of the build)

These files are **reference templates**, intentionally NOT a Gradle module and NOT in
`settings.gradle.kts` — they depend on Firebase Cloud Messaging, which needs your own
`google-services.json` and would break the buildable `:example` app without it.

To use them in your app:

1. Add Firebase to your app module:
   ```kotlin
   plugins { id("com.google.gms.google-services") }
   dependencies { implementation("com.google.firebase:firebase-messaging:24.+") }
   ```
   and drop your `google-services.json` into the app module.
2. Copy `RTCstackMessagingService.kt` + `IncomingCallActivity.kt` into your app, register the
   service + activity in your `AndroidManifest.xml`, and declare `POST_NOTIFICATIONS` +
   `USE_FULL_SCREEN_INTENT`.
3. Send a **high-priority data message** from your backend with
   `{ roomId, callerId, callerName }` when a call starts.
4. On Accept, mint a token from your backend (`POST /v1/token`) and connect — the activity
   shows where.

This mirrors iOS's `CallCoordinator`: push → surface call UI → on accept, mint token → connect.
The Android baseline is a full-screen-intent notification; full Telecom `ConnectionService`
self-management is an optional heavier upgrade.
