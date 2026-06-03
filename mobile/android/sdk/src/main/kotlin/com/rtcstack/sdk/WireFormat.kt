package com.rtcstack.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Data-channel wire format.
 *
 * CROSS-PLATFORM CONTRACT — this MUST stay byte-compatible with the shipped web SDK
 * (`packages/sdk/src/call.ts`), because native and web clients share rooms.
 *
 * IMPORTANT: this mirrors the *shipped* call.ts, which differs from
 * `development/sdk/api.md`. The shipped format is:
 *   chat:       { "type": "chat", "text": "...", "id": "..." }
 *   reaction:   { "type": "reaction", "emoji": "..." }
 *   speaking:   { "type": "speaking", "speakerId": "...", "speaker": "..." }
 *   transcript: { "type": "transcript", "text": "...", "speakerId": "...",
 *                 "speaker": "...", "startMs": 1234 }
 * Unknown `type` values are ignored (forward-compat).
 *
 * See "wire-format divergence" note in MAC_HANDOFF.md — reconcile api.md to this, or
 * version-bump both SDKs together, before GA.
 */
internal object WireFormat {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    /** Encode an outgoing chat message exactly as call.ts does: {type,text,id}. */
    fun encodeChat(text: String, id: String): ByteArray =
        buildJsonObject {
            put("type", JsonPrimitive("chat"))
            put("text", JsonPrimitive(text))
            put("id", JsonPrimitive(id))
        }.toString().toByteArray(Charsets.UTF_8)

    /** Encode an outgoing reaction: {type,emoji}. */
    fun encodeReaction(emoji: String): ByteArray =
        buildJsonObject {
            put("type", JsonPrimitive("reaction"))
            put("emoji", JsonPrimitive(emoji))
        }.toString().toByteArray(Charsets.UTF_8)

    /** Parsed representation of an inbound data-channel payload. */
    sealed interface Inbound {
        data class Chat(val text: String, val id: String?) : Inbound
        data class Reaction(val emoji: String) : Inbound
        data class Speaking(val speakerId: String?, val speaker: String?) : Inbound
        data class Transcript(
            val text: String,
            val speakerId: String?,
            val speaker: String?,
            val startMs: Long?,
        ) : Inbound
        /** Recognised JSON but unknown/irrelevant type — caller ignores. */
        data object Ignored : Inbound
    }

    /** Decode an inbound payload. Returns null on malformed (non-JSON) data — caller ignores. */
    fun decode(bytes: ByteArray): Inbound? {
        return try {
            val obj: JsonObject = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "chat" -> {
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return Inbound.Ignored
                    Inbound.Chat(text, obj["id"]?.jsonPrimitive?.contentOrNull)
                }
                "reaction" -> {
                    val emoji = obj["emoji"]?.jsonPrimitive?.contentOrNull ?: return Inbound.Ignored
                    Inbound.Reaction(emoji)
                }
                "speaking" -> Inbound.Speaking(
                    obj["speakerId"]?.jsonPrimitive?.contentOrNull,
                    obj["speaker"]?.jsonPrimitive?.contentOrNull,
                )
                "transcript" -> {
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return Inbound.Ignored
                    Inbound.Transcript(
                        text = text,
                        speakerId = obj["speakerId"]?.jsonPrimitive?.contentOrNull,
                        speaker = obj["speaker"]?.jsonPrimitive?.contentOrNull,
                        startMs = obj["startMs"]?.jsonPrimitive?.longOrNull,
                    )
                }
                else -> Inbound.Ignored
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Parse a LiveKit participant metadata JSON string into a plain map (mirrors call.ts mapParticipant). */
@Serializable
private class MetadataRoot

internal fun parseMetadata(raw: String?): Map<String, Any?> {
    if (raw.isNullOrBlank()) return emptyMap()
    return try {
        val obj = WireFormat.json.parseToJsonElement(raw).jsonObject
        obj.mapValues { (_, v) ->
            (v as? JsonPrimitive)?.let { it.contentOrNull } ?: v.toString()
        }
    } catch (_: Exception) {
        emptyMap()
    }
}
