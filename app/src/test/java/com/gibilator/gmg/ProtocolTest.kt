package com.gibilator.gmg

import com.gibilator.gmg.protocol.FireState
import com.gibilator.gmg.protocol.GmgInvalidValueException
import com.gibilator.gmg.protocol.GmgProtocolException
import com.gibilator.gmg.protocol.PowerState
import com.gibilator.gmg.protocol.Protocol
import com.gibilator.gmg.protocol.WarnCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity tests for the wire protocol. Frames are built with the LIVE
 * `protocol.py` offsets (which the real grill confirms) — NOT the stale offsets
 * baked into the integration's committed `test_protocol.py`. Behavioural
 * expectations (sentinels, low-pellet, cold-smoke, command bytes, ranges) match
 * the Python tests.
 */
class ProtocolTest {

    private fun putU16(buf: ByteArray, o: Int, value: Int) {
        buf[o] = (value and 0xFF).toByte()
        buf[o + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun putU32(buf: ByteArray, o: Int, value: Long) {
        buf[o] = (value and 0xFF).toByte()
        buf[o + 1] = ((value shr 8) and 0xFF).toByte()
        buf[o + 2] = ((value shr 16) and 0xFF).toByte()
        buf[o + 3] = ((value shr 24) and 0xFF).toByte()
    }

    /** Build a 36-byte frame using the live/hardware offsets. */
    private fun makeFrame(
        header: ByteArray = byteArrayOf(0x55, 0x52),
        grillTemp: Int = 225,
        probe1: Int = 140,
        grillSet: Int = 225,
        probe2: Int = 89,
        probe2Target: Int = 145,
        profileRemaining: Long = 0,
        warnCode: Long = 0,
        probe1Target: Int = 165,
        powerState: Int = 1,
        fireState: Int = 3,
        hopperPct: Int = 80,
        grillType: Int = 3,
    ): ByteArray {
        val f = ByteArray(36)
        f[0] = header[0]; f[1] = header[1]
        putU16(f, 2, grillTemp)
        putU16(f, 4, probe1)
        putU16(f, 6, grillSet)
        putU16(f, 16, probe2)
        putU16(f, 18, probe2Target)
        putU32(f, 20, profileRemaining)
        putU32(f, 24, warnCode)
        putU16(f, 28, probe1Target)
        f[30] = (powerState and 0xFF).toByte()
        f[32] = (fireState and 0xFF).toByte()
        f[33] = (hopperPct and 0xFF).toByte()
        f[35] = (grillType and 0xFF).toByte()
        return f
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    // --- parse tests -------------------------------------------------------

    @Test
    fun parseBaselineFrame() {
        val snap = Protocol.parseStatusFrame(
            makeFrame(grillTemp = 225, probe1 = 140, grillSet = 225, powerState = 1, fireState = 3, warnCode = 0),
        )
        assertEquals(225, snap.grillTemp)
        assertEquals(225, snap.grillSetTemp)
        assertEquals(140, snap.probe1Temp)
        assertEquals(165, snap.probe1Target)
        assertEquals(PowerState.ON, snap.powerState)
        assertEquals(FireState.RUNNING, snap.fireState)
        assertTrue(snap.flameOn)
        assertFalse(snap.lowPellet)
        assertFalse(snap.coldSmoke)
    }

    @Test
    fun probeSentinelMeansUnplugged() {
        val snap = Protocol.parseStatusFrame(makeFrame(probe1 = 89))
        assertNull(snap.probe1Temp)
    }

    @Test
    fun lowPelletCode8() {
        assertTrue(Protocol.parseStatusFrame(makeFrame(warnCode = 8)).lowPellet)
        assertEquals(WarnCode.LOW_PELLET, Protocol.parseStatusFrame(makeFrame(warnCode = 8)).warnCode)
    }

    @Test
    fun lowPelletCode128Alias() {
        val snap = Protocol.parseStatusFrame(makeFrame(warnCode = 128))
        assertTrue(snap.lowPellet)
        assertEquals(WarnCode.LOW_PELLET, snap.warnCode)
    }

    @Test
    fun coldSmokePowerState() {
        assertTrue(Protocol.parseStatusFrame(makeFrame(powerState = 3)).coldSmoke)
    }

    @Test
    fun shortFrameRaises() {
        assertFailsWith<GmgProtocolException> {
            Protocol.parseStatusFrame(byteArrayOf(0x55, 0x52, 0x00, 0x00))
        }
    }

    @Test
    fun wrongHeaderRaises() {
        assertFailsWith<GmgProtocolException> {
            Protocol.parseStatusFrame(makeFrame(header = byteArrayOf(0x00, 0x00)))
        }
    }

    // --- golden frame captured from the real grill (GMG12137138, Trek) -----

    @Test
    fun parsesRealCapturedFrame() {
        // 55-byte reply captured live from 192.168.1.148 via UR001!. The parser
        // uses the first 36 bytes; the tail is firmware ASCII ("DC01SUF07.1").
        val hex = "555248004a009600060314321919000000000000" +
            "ffffffff0000000000000000010000010000f700" +
            "fd444330315355463037" + "2e3100" + "1cfe"
        val snap = Protocol.parseStatusFrame(hexToBytes(hex))
        assertEquals(72, snap.grillTemp)
        assertEquals(150, snap.grillSetTemp)
        assertEquals(74, snap.probe1Temp)
        assertEquals(PowerState.OFF, snap.powerState)
        assertEquals(FireState.OFF, snap.fireState)
        assertEquals(1, snap.grillType)
        assertEquals("Trek", Protocol.modelNameFor(snap.grillType))
        assertEquals(WarnCode.NONE, snap.warnCode)
        assertFalse(snap.lowPellet)
        assertFalse(snap.coldSmoke)
        assertFalse(snap.flameOn)
    }

    // --- command builders --------------------------------------------------

    @Test
    fun buildSetGrillTempBasic() {
        assertEquals("UT225!", String(Protocol.encodeSetGrillTemp(225), Charsets.US_ASCII))
    }

    @Test
    fun buildSetProbeTargetProbe1Uppercase() {
        assertEquals("UF165!", String(Protocol.encodeSetProbeTarget(1, 165), Charsets.US_ASCII))
    }

    @Test
    fun buildSetProbeTargetProbe2Lowercase() {
        assertEquals("Uf145!", String(Protocol.encodeSetProbeTarget(2, 145), Charsets.US_ASCII))
    }

    @Test
    fun buildSetGrillTempOutOfRangeRaises() {
        for (bad in intArrayOf(-1, 0, 149, 551, 1000)) {
            assertFailsWith<GmgInvalidValueException>("expected throw for $bad") {
                Protocol.encodeSetGrillTemp(bad)
            }
        }
    }

    @Test
    fun buildSetProbeTargetOutOfRangeRaises() {
        for (bad in intArrayOf(-1, 0, 31, 258, 999)) {
            assertFailsWith<GmgInvalidValueException>("expected throw for $bad") {
                Protocol.encodeSetProbeTarget(1, bad)
            }
        }
    }
}
