package com.rtcstack.sdk

import android.content.Context

/**
 * Entry point for the RTCstack Android SDK. Mirrors `createCall` from `@rtcstack/sdk`.
 *
 * ```kotlin
 * val call = RTCstack.createCall(context, CallOptions(token = jwt, url = wssUrl))
 * call.connect()
 * ```
 *
 * The SDK only ever holds a LiveKit JWT + WSS URL — never the RTCstack API key/secret.
 * Mint tokens on your backend (POST /v1/token) and pass them in.
 */
public object RTCstack {
    public fun createCall(context: Context, options: CallOptions): Call =
        Call(context.applicationContext, options)
}
