package com.gibilator.gmg.cook

/**
 * Plain-language copy for a first-time BBQ user. Pure mapping from cook-state /
 * phase keys to friendly, reassuring strings. No raw enum strings ever reach the
 * user through here.
 */
object CookCopy {

    /** Friendly headline for a [CookState]. */
    fun stateHeadline(state: CookState): String = when (state) {
        CookState.IDLE -> "Ready when you are"
        CookState.PLANNED -> "Getting set up…"
        CookState.PREHEATING -> "Heating up the grill"
        CookState.WAITING_MEAT -> "Grill's hot — put your food on"
        CookState.COOKING -> "Cooking"
        CookState.APPROACHING -> "Almost done!"
        CookState.PULL_REACHED -> "It's ready! 🎉"
        CookState.COMPLETE -> "All done"
        CookState.ABORTED -> "Stopped"
    }

    /** Friendly name for a [CookPhysics.phaseAt] key. */
    fun phaseLabel(phaseKey: String): String = when (phaseKey) {
        "pre_stall" -> "Warming through"
        "stall" -> "The Stall — totally normal"
        "post_stall" -> "Pushing to the finish"
        "single_phase" -> "Cooking through"
        "approaching" -> "Almost there!"
        "pull_reached" -> "Done!"
        else -> "Cooking"
    }

    /**
     * One or two sentences explaining what's happening now and what to expect,
     * driven by the current phase. [deltaF] = expected − actual (>0 = behind).
     */
    fun whatsHappening(phaseKey: String, deltaF: Double?, pullF: Int, tempUnit: String): String {
        val pull = if (tempUnit == "C") "${((pullF - 32) * 5 / 9.0).toInt()}°C" else "$pullF°F"
        return when (phaseKey) {
            "pre_stall" -> "Your food is warming up nicely. Sit back — the first hour or two is just gentle smoke."
            "stall" ->
                "Temperature's holding steady around the 160s. This is the famous “stall” — " +
                    "moisture on the surface is cooling the meat as fast as it heats. It can last an hour or more. " +
                    "Totally normal. Don't open the lid 👍"
            "post_stall" -> "Through the stall and climbing again. The home stretch toward $pull."
            "single_phase" -> "Cooking steadily toward $pull. This one doesn't stall, so it moves at a nice even pace."
            "approaching" -> "Within a few degrees of $pull. Get your plates ready — almost time to pull it."
            "pull_reached" -> "It's hit $pull. Pull it off, let it rest, and dig in!"
            else -> "Cooking along nicely."
        }.let { base ->
            when {
                deltaF == null -> base
                deltaF > 8 -> "$base\n\nRunning a touch behind schedule — that's okay, low-and-slow rewards patience."
                deltaF < -8 -> "$base\n\nRunning a little ahead of schedule — looking good."
                else -> "$base\n\nRight on track ✓"
            }
        }
    }
}
