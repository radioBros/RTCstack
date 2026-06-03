package com.rtcstack.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-platform wire-format contract tests.
 *
 * The contract is SEMANTIC compatibility with the SHIPPED web SDK (`packages/sdk/src/call.ts`),
 * NOT byte-identity: the web client does `JSON.parse(text)` and reads `data.type`/`data.text`/
 * `data.id`/`data.emoji`. So we assert the parsed JSON has the right keys/values — key order
 * and emoji escaping are irrelevant to interop. A native client and a web client in the same
 * room must be able to decode each other's payloads.
 */
class WireFormatTest {

    private fun parse(bytes: ByteArray) = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject

    @Test
    fun `chat encodes with keys the web client reads`() {
        val obj = parse(WireFormat.encodeChat("Hello everyone!", "42"))
        assertEquals("chat", obj["type"]?.jsonPrimitive?.content)
        assertEquals("Hello everyone!", obj["text"]?.jsonPrimitive?.content)
        assertEquals("42", obj["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `reaction encodes with keys the web client reads`() {
        val obj = parse(WireFormat.encodeReaction("👍"))
        assertEquals("reaction", obj["type"]?.jsonPrimitive?.content)
        assertEquals("👍", obj["emoji"]?.jsonPrimitive?.content)
    }

    @Test
    fun `encode then decode round-trips`() {
        val decoded = WireFormat.decode(WireFormat.encodeChat("round trip", "99"))
        assertTrue(decoded is WireFormat.Inbound.Chat)
        decoded as WireFormat.Inbound.Chat
        assertEquals("round trip", decoded.text)
        assertEquals("99", decoded.id)
    }

    @Test
    fun `decodes web chat payload`() {
        val inbound = WireFormat.decode("""{"type":"chat","text":"hi","id":"7"}""".toByteArray())
        assertTrue(inbound is WireFormat.Inbound.Chat)
        inbound as WireFormat.Inbound.Chat
        assertEquals("hi", inbound.text)
        assertEquals("7", inbound.id)
    }

    @Test
    fun `decodes transcript with startMs`() {
        val inbound = WireFormat.decode(
            """{"type":"transcript","text":"hello","speakerId":"u1","speaker":"Al","startMs":1234}""".toByteArray(),
        )
        assertTrue(inbound is WireFormat.Inbound.Transcript)
        inbound as WireFormat.Inbound.Transcript
        assertEquals(1234L, inbound.startMs)
        assertEquals("u1", inbound.speakerId)
    }

    @Test
    fun `unknown type is ignored not crashed`() {
        assertEquals(WireFormat.Inbound.Ignored, WireFormat.decode("""{"type":"future_thing"}""".toByteArray()))
    }

    @Test
    fun `malformed payload returns null`() {
        assertNull(WireFormat.decode("not json".toByteArray()))
    }
}
