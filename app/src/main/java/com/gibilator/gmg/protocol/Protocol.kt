package com.gibilator.gmg.protocol

/**
 * Frame parsing + command encoding for the GMG wire protocol.
 *
 * Pure logic, no I/O. Direct port of `api/protocol.py`. The status frame is the
 * first [Const.STATUS_FRAME_LEN] bytes of the reply, little-endian.
 */
object Protocol {

    /** grill_type byte -> human model name. Port of `MODEL_NAMES`. */
    val MODEL_NAMES: Map<Int, String> = mapOf(
        0 to "Davy Crockett",
        1 to "Trek",
        2 to "Daniel Boone",
        3 to "Jim Bowie",
        4 to "Ledge",
        5 to "Peak",
        6 to "Ledge Prime+",
        7 to "Peak Prime+",
        8 to "Trek Prime 2.0",
        9 to "Ledge Prime 2.0",
        10 to "Peak Prime 2.0",
        11 to "Daniel Boone Prime+",
        12 to "Jim Bowie Prime+",
        13 to "Daniel Boone Prime 2.0",
        14 to "Jim Bowie Prime 2.0",
        15 to "Trek Prime+",
    )

    const val DEFAULT_MODEL_NAME = "Green Mountain Grill"

    fun modelNameFor(grillType: Int): String = MODEL_NAMES[grillType] ?: DEFAULT_MODEL_NAME

    private fun u16(data: ByteArray, o: Int): Int =
        (data[o].toInt() and 0xFF) or ((data[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(data: ByteArray, o: Int): Long =
        (data[o].toLong() and 0xFF) or
            ((data[o + 1].toLong() and 0xFF) shl 8) or
            ((data[o + 2].toLong() and 0xFF) shl 16) or
            ((data[o + 3].toLong() and 0xFF) shl 24)

    private fun probeOrNull(value: Int): Int? =
        if (value == Const.PROBE_UNPLUGGED_SENTINEL) null else value

    /** Parse a status frame into a [GmgSnapshot]. */
    fun parseStatusFrame(data: ByteArray): GmgSnapshot {
        if (data.size < Const.STATUS_FRAME_LEN) {
            throw GmgProtocolException(
                "status frame too short: got ${data.size} bytes, need ${Const.STATUS_FRAME_LEN}",
            )
        }
        if (data[0] != Const.STATUS_HEADER[0] || data[1] != Const.STATUS_HEADER[1]) {
            throw GmgProtocolException(
                "status frame header mismatch: ${data[0]},${data[1]} != UR",
            )
        }

        val grillTemp = u16(data, 2)
        val probe1Raw = u16(data, 4)
        val grillSetTemp = u16(data, 6)
        val probe2Raw = u16(data, 16)
        val probe2Target = u16(data, 18)
        val profileTimeRemainingS = u32(data, 20)
        val warnRaw = u32(data, 24)
        val probe1Target = u16(data, 28)

        val powerState = PowerState.from(data[30].toInt() and 0xFF)
        val fireState = FireState.from(data[32].toInt() and 0xFF)
        val hopperPct = data[33].toInt() and 0xFF
        val grillType = data[35].toInt() and 0xFF

        val warnValue = warnRaw and 0xFFFF_FFFFL
        val lowPellet = warnValue == WarnCode.LOW_PELLET.raw.toLong() ||
            warnValue == Const.LOW_PELLET_ALT_VALUE.toLong()
        val warnCode = if (warnValue == Const.LOW_PELLET_ALT_VALUE.toLong()) {
            WarnCode.LOW_PELLET
        } else {
            WarnCode.from(warnValue.toInt())
        }

        return GmgSnapshot(
            grillTemp = grillTemp,
            grillSetTemp = grillSetTemp,
            probe1Temp = probeOrNull(probe1Raw),
            probe1Target = probe1Target,
            probe2Temp = probeOrNull(probe2Raw),
            probe2Target = probe2Target,
            powerState = powerState,
            fireState = fireState,
            warnCode = warnCode,
            lowPellet = lowPellet,
            fanOverload = warnCode == WarnCode.FAN_OVERLOAD,
            augerOverload = warnCode == WarnCode.AUGER_OVERLOAD,
            ignitorOverload = warnCode == WarnCode.IGNITOR_OVERLOAD,
            lowVoltage = warnCode == WarnCode.LOW_VOLTAGE,
            fanDisconnect = warnCode == WarnCode.FAN_DISCONNECT,
            augerDisconnect = warnCode == WarnCode.AUGER_DISCONNECT,
            ignitorDisconnect = warnCode == WarnCode.IGNITOR_DISCONNECT,
            flameOn = fireState == FireState.RUNNING,
            coldSmoke = powerState == PowerState.COLD_SMOKE || fireState == FireState.COLD_SMOKE,
            hopperPct = hopperPct,
            grillType = grillType,
            profileTimeRemainingS = profileTimeRemainingS,
            raw = data.copyOf(Const.STATUS_FRAME_LEN),
        )
    }

    /** Validate + encode a `UT###!` grill setpoint command. */
    fun encodeSetGrillTemp(fahrenheit: Int): ByteArray {
        if (fahrenheit !in Const.GRILL_TEMP_MIN..Const.GRILL_TEMP_MAX) {
            throw GmgInvalidValueException(
                "grill setpoint $fahrenheit out of range " +
                    "[${Const.GRILL_TEMP_MIN}, ${Const.GRILL_TEMP_MAX}]",
            )
        }
        return "UT%03d!".format(fahrenheit).toByteArray(Charsets.US_ASCII)
    }

    /** Validate + encode `UF###!` (probe 1) or `Uf###!` (probe 2). */
    fun encodeSetProbeTarget(probe: Int, fahrenheit: Int): ByteArray {
        if (probe != 1 && probe != 2) {
            throw GmgInvalidValueException("probe must be 1 or 2, got $probe")
        }
        if (fahrenheit !in Const.PROBE_TEMP_MIN..Const.PROBE_TEMP_MAX) {
            throw GmgInvalidValueException(
                "probe setpoint $fahrenheit out of range " +
                    "[${Const.PROBE_TEMP_MIN}, ${Const.PROBE_TEMP_MAX}]",
            )
        }
        val letter = if (probe == 1) "F" else "f"
        return "U$letter%03d!".format(fahrenheit).toByteArray(Charsets.US_ASCII)
    }

    /** Cheap predicate: is this reply a status frame? */
    fun isStatusFrame(data: ByteArray): Boolean =
        data.size >= Const.STATUS_FRAME_LEN &&
            data[0] == Const.STATUS_HEADER[0] &&
            data[1] == Const.STATUS_HEADER[1]
}
