# Consumer ProGuard/R8 rules — automatically applied to any app that depends on
# com.rtcstack:sdk. Integrators inherit these; they do not need to copy anything.

# WebRTC native bindings (reflection from native).
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# LiveKit relies on protobuf + reflection.
-keep class io.livekit.** { *; }
-keep class livekit.** { *; }
-dontwarn io.livekit.**

# kotlinx.serialization generated serializers for RTCstack wire-format models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.rtcstack.sdk.** {
    *** Companion;
}
-keepclasseswithmembers class com.rtcstack.sdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
