package com.padraik99.grindhaus.domain

/** A grinder's dial, as far as the advice needs to know it. */
data class GrindScale(
    val min: Double,
    val max: Double,
    val step: Double,
    /** On the Maestri House, lower numbers are finer. */
    val finerIsLower: Boolean,
)

data class GrindSuggestion(val grind: Double, val reason: String)

/**
 * The textbook dial-in rule: sour usually means under-extracted, so go finer;
 * bitter usually means over-extracted, so go coarser; balanced means leave it.
 * One step at a time, never past the end of the dial.
 */
object GrindAdvisor {

    fun suggest(lastTaste: Taste?, lastGrind: Double?, scale: GrindScale): GrindSuggestion? {
        if (lastTaste == null || lastGrind == null || lastTaste == Taste.BALANCED) return null
        val finer = lastTaste == Taste.SOUR
        val direction = if (finer == scale.finerIsLower) -1 else 1
        val next = (lastGrind + direction * scale.step).coerceIn(scale.min, scale.max)
        if (next == lastGrind) return null
        val shown = if (next % 1.0 == 0.0) next.toInt().toString() else next.toString()
        val reason = if (finer) "Ran sour. Try grind $shown, one step finer?" else "Ran bitter. Try grind $shown, one step coarser?"
        return GrindSuggestion(next, reason)
    }
}
