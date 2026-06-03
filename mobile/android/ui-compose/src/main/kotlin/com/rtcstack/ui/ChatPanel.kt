package com.rtcstack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rtcstack.sdk.Call
import com.rtcstack.sdk.Message
import kotlinx.coroutines.launch

/**
 * Chat panel mirroring `ChatPanel.tsx`. Renders [Call.messages] plus a composer.
 *
 * NOTE: the SDK does NOT echo the local user's own outgoing messages (LiveKit doesn't loop data
 * back to the sender), so this panel keeps a local "sent" list and merges it with received
 * messages — exactly the responsibility called out in the SDK docs.
 */
@Composable
public fun ChatPanel(
    call: Call,
    localId: String,
    localName: String,
    modifier: Modifier = Modifier,
) {
    val colors = RTCstackTheme.colors
    val scope = rememberCoroutineScope()
    val received by call.messages.collectAsStateWithLifecycle()
    val sent = remember { mutableStateListOf<Message>() }
    var draft by remember { mutableStateOf("") }
    var counter by remember { mutableStateOf(0L) }

    val all = remember(received, sent.size) { (received + sent).sortedBy { it.timestamp } }

    Column(modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            items(all, key = { it.id }) { msg ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(msg.fromName, color = colors.accent, fontWeight = FontWeight.SemiBold)
                    Text(msg.text, color = colors.text)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…") },
            )
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@Button
                    counter += 1
                    sent.add(
                        Message(
                            id = "local-$counter",
                            from = localId,
                            fromName = localName,
                            text = text,
                            timestamp = System.currentTimeMillis(),
                            to = null,
                        ),
                    )
                    draft = ""
                    scope.launch { call.sendMessage(text) }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Send") }
        }
    }
}
