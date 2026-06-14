package com.gibilator.gmg.protocol

/**
 * Wire-protocol constants for the Green Mountain Grills UDP interface.
 *
 * Direct port of `api/const.py` from the HA integration. The controller listens
 * on UDP/8080 and accepts ASCII commands terminated with `!`. The poll command
 * `UR001!` returns a binary status frame whose first 36 bytes are little-endian.
 */
object Const {
    const val DEFAULT_PORT = 8080
    const val DEFAULT_BROADCAST = "255.255.255.255"

    /** Per-request socket timeout in milliseconds (1.0s in the Python source). */
    const val DEFAULT_REQUEST_TIMEOUT_MS = 1000
    const val DEFAULT_MAX_RETRIES = 5
    const val DEFAULT_DISCOVERY_TIMEOUT_MS = 2000

    val CMD_STATUS = "UR001!".toByteArray(Charsets.US_ASCII)
    val CMD_SERIAL = "UL!".toByteArray(Charsets.US_ASCII)
    val CMD_FIRMWARE = "UN!".toByteArray(Charsets.US_ASCII)
    val CMD_POWER_ON = "UK001!".toByteArray(Charsets.US_ASCII)
    val CMD_COLD_SMOKE = "UK002!".toByteArray(Charsets.US_ASCII)
    val CMD_POWER_OFF = "UK004!".toByteArray(Charsets.US_ASCII)

    const val STATUS_FRAME_LEN = 36
    val STATUS_HEADER = "UR".toByteArray(Charsets.US_ASCII)

    /** Probe temp value that means "probe unplugged" -> reported as null. */
    const val PROBE_UNPLUGGED_SENTINEL = 89

    /** Alternate raw warn value that also means low pellet. */
    const val LOW_PELLET_ALT_VALUE = 128

    const val GRILL_TEMP_MIN = 150
    const val GRILL_TEMP_MAX = 550
    const val PROBE_TEMP_MIN = 32
    const val PROBE_TEMP_MAX = 257

    const val SERIAL_PREFIX = "GMG"
}
