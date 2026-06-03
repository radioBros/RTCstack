package com.rtcstack.sdk.audio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Thin audio-routing helper for in-call audio output (earpiece / speaker / Bluetooth).
 *
 * NOTE: livekit-android already installs an `AudioSwitchHandler` that requests audio focus
 * and sets `MODE_IN_COMMUNICATION` for the duration of the call — you usually do NOT manage
 * focus yourself. This helper only covers the user-facing *route* choice (the "speaker"
 * toggle in a call UI), which apps commonly need to drive explicitly.
 *
 * On API 31+ this uses the modern `setCommunicationDevice` API; below that it falls back to
 * `isSpeakerphoneOn` + Bluetooth SCO.
 */
public class AudioRouter(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    public enum class Route { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }

    /** Available output routes right now (best-effort; verify device-type mapping on hardware). */
    public fun availableRoutes(): List<Route> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val routes = mutableListOf(Route.EARPIECE, Route.SPEAKER)
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoAvailableOffCall) routes += Route.BLUETOOTH
            @Suppress("DEPRECATION")
            if (audioManager.isWiredHeadsetOn) routes += Route.WIRED_HEADSET
            return routes
        }
        return audioManager.availableCommunicationDevices.mapNotNull { it.toRoute() }.distinct()
    }

    /** Set the active output route. */
    public fun setRoute(route: Route) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRouteApi31(route)
        } else {
            setRouteLegacy(route)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setRouteApi31(route: Route) {
        val target = audioManager.availableCommunicationDevices.firstOrNull { it.toRoute() == route }
        if (target != null) {
            audioManager.setCommunicationDevice(target)
        } else if (route == Route.EARPIECE) {
            audioManager.clearCommunicationDevice()
        }
    }

    @Suppress("DEPRECATION")
    private fun setRouteLegacy(route: Route) {
        when (route) {
            Route.SPEAKER -> {
                audioManager.stopBluetoothSco()
                audioManager.isSpeakerphoneOn = true
            }
            Route.BLUETOOTH -> {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
            Route.EARPIECE, Route.WIRED_HEADSET -> {
                audioManager.stopBluetoothSco()
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun android.media.AudioDeviceInfo.toRoute(): Route? = when (type) {
        android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> Route.EARPIECE
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Route.SPEAKER
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> Route.BLUETOOTH
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Route.WIRED_HEADSET
        else -> null
    }
}
