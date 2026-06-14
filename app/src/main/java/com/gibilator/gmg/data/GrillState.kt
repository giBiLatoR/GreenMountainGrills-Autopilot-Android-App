package com.gibilator.gmg.data

import com.gibilator.gmg.cook.CookPhysics.CookProjection
import com.gibilator.gmg.protocol.GmgGrillInfo
import com.gibilator.gmg.protocol.GmgSnapshot

/** Connection lifecycle for the active grill. */
enum class ConnState { Disconnected, Connecting, Connected, ServerMode, Error }

/** One charted sample (food actual / expected / grill over time). */
data class Sample(
    val tMs: Long,
    val grillF: Int,
    val probeF: Int?,
    val expectedF: Double?,
)

/** A view of the active cook for the UI (numbers + phase key; copy resolved in UI). */
data class CookView(
    val active: Boolean,
    val rawState: String,
    val meatKey: String?,
    val meatLabel: String?,
    val pitTargetF: Int?,
    val pullF: Int?,
    val expectedProbeF: Double?,
    val probeF: Int?,
    val elapsedMin: Double?,
    val remainingMin: Double?,
    val onSchedule: Boolean?,
    val phaseKey: String?,
    val deltaF: Double?,
    val projection: CookProjection?,
    val probeIndex: Int,
)

/** Everything the UI renders for the selected grill. Immutable snapshot. */
data class GrillUiState(
    val conn: ConnState = ConnState.Disconnected,
    val info: GmgGrillInfo? = null,
    val snapshot: GmgSnapshot? = null,
    val cook: CookView? = null,
    val samples: List<Sample> = emptyList(),
    val error: String? = null,
)
