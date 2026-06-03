// Root build file. Plugin versions are declared here with `apply false`
// and applied in each module. Keep versions in lockstep with gradle/libs.versions.toml.
plugins {
    id("com.android.library") version "8.5.2" apply false
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.vanniktech.maven.publish") version "0.29.0" apply false
}

// RTCstack mobile SDK version — keep the MAJOR in lockstep with @rtcstack/sdk.
// The SDK public contract (events, wire format) is a cross-platform commitment.
val rtcstackVersion by extra("1.0.2")
val rtcstackGroup by extra("com.rtcstack")
