package com.rtcstack.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rtcstack.sdk.Call
import com.rtcstack.sdk.CallOptions
import com.rtcstack.sdk.RTCstack
import com.rtcstack.sdk.call.RTCstackCallService
import com.rtcstack.ui.RTCstackTheme
import com.rtcstack.ui.VideoConference
import kotlinx.coroutines.launch

/**
 * Minimal example: a join form (paste a LiveKit JWT + WSS URL from your backend's POST /v1/token)
 * → drop-in [VideoConference]. Mirrors `apps/examples/react-example`.
 *
 * In a real app you would mint the token from your authenticated backend, never paste it.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RTCstackTheme {
                val scope = rememberCoroutineScope()
                var call by remember { mutableStateOf<Call?>(null) }
                var token by remember { mutableStateOf("") }
                var url by remember { mutableStateOf("wss://") }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { /* proceed regardless; tracks publish if granted */ }

                val active = call
                if (active == null) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("RTCstack — Join a call")
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("WSS URL") })
                        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token (JWT)") })
                        Button(onClick = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
                            val c = RTCstack.createCall(this@MainActivity, CallOptions(token = token.trim(), url = url.trim()))
                            call = c
                            RTCstackCallService.start(this@MainActivity)
                            scope.launch {
                                runCatching { c.connect() }
                                    .onSuccess { c.setMicEnabled(true); c.setCameraEnabled(true) }
                            }
                        }) { Text("Join") }
                    }
                } else {
                    VideoConference(
                        call = active,
                        onLeave = {
                            RTCstackCallService.stop(this@MainActivity)
                            active.release()
                            call = null
                        },
                    )
                }
            }
        }
    }
}
