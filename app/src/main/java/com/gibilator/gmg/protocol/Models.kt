package com.gibilator.gmg.protocol

/** Power state byte (offset 30) of the status frame. Port of `PowerState`. */
enum class PowerState(val raw: Int) {
    OFF(0), ON(1), FAN(2), COLD_SMOKE(3);

    companion object {
        fun from(raw: Int): PowerState = entries.firstOrNull { it.raw == raw } ?: OFF
    }
}

/** Fire state byte (offset 32) of the status frame. Port of `FireState`. */
enum class FireState(val raw: Int) {
    DEFAULT(0), OFF(1), STARTUP(2), RUNNING(3), COOL_DOWN(4), FAIL(5), COLD_SMOKE(198);

    companion object {
        fun from(raw: Int): FireState = entries.firstOrNull { it.raw == raw } ?: DEFAULT
    }
}

/** Warning code (bytes 24-27) of the status frame. Port of `WarnCode`. */
enum class WarnCode(val raw: Int) {
    NONE(0),
    FAN_OVERLOAD(1),
    AUGER_OVERLOAD(2),
    IGNITOR_OVERLOAD(3),
    LOW_VOLTAGE(4),
    FAN_DISCONNECT(5),
    AUGER_DISCONNECT(6),
    IGNITOR_DISCONNECT(7),
    LOW_PELLET(8);

    companion object {
        fun from(raw: Int): WarnCode = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/**
 * Parsed view of one 36-byte status frame. Port of the `GMGSnapshot` dataclass.
 *
 * Temperatures are Fahrenheit. [probe1Temp] / [probe2Temp] are null when the
 * probe reads the unplugged sentinel.
 */
data class GmgSnapshot(
    val grillTemp: Int,
    val grillSetTemp: Int,
    val probe1Temp: Int?,
    val probe1Target: Int,
    val probe2Temp: Int?,
    val probe2Target: Int,
    val powerState: PowerState,
    val fireState: FireState,
    val warnCode: WarnCode,
    val lowPellet: Boolean,
    val fanOverload: Boolean,
    val augerOverload: Boolean,
    val ignitorOverload: Boolean,
    val lowVoltage: Boolean,
    val fanDisconnect: Boolean,
    val augerDisconnect: Boolean,
    val ignitorDisconnect: Boolean,
    val flameOn: Boolean,
    val coldSmoke: Boolean,
    val hopperPct: Int,
    val grillType: Int,
    val profileTimeRemainingS: Long,
    val raw: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GmgSnapshot) return false
        // raw excluded from equality on purpose (compare logical fields only).
        return grillTemp == other.grillTemp &&
            grillSetTemp == other.grillSetTemp &&
            probe1Temp == other.probe1Temp &&
            probe1Target == other.probe1Target &&
            probe2Temp == other.probe2Temp &&
            probe2Target == other.probe2Target &&
            powerState == other.powerState &&
            fireState == other.fireState &&
            warnCode == other.warnCode &&
            hopperPct == other.hopperPct &&
            grillType == other.grillType &&
            profileTimeRemainingS == other.profileTimeRemainingS
    }

    override fun hashCode(): Int {
        var result = grillTemp
        result = 31 * result + grillSetTemp
        result = 31 * result + powerState.hashCode()
        result = 31 * result + fireState.hashCode()
        result = 31 * result + grillType
        return result
    }
}

/** Identity bundle returned by [com.gibilator.gmg.net.GmgClient.probe]. */
data class GmgGrillInfo(
    val host: String,
    val serial: String,
    val firmware: String,
    val model: String,
    val modelId: Int,
    val snapshot: GmgSnapshot,
)

/** One unique grill returned by broadcast discovery. */
data class DiscoveredGrill(
    val host: String,
    val serial: String,
)
