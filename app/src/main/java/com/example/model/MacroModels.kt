package com.example.model

import java.util.UUID

/**
 * Supported execution action types for Macro instructions.
 */
enum class ActionType {
    TAP,
    SWIPE,
    DELAY,
    LOOP_START,
    LOOP_END
}

/**
 * Screen dimensions recorded when the macro was authored.
 */
data class TargetResolution(
    val width: Int = 1080,
    val height: Int = 2400
)

/**
 * Individual atomic or control-flow step within a macro profile.
 *
 * All coordinates are normalized between 0.0f and 1.0f relative to screen boundaries.
 */
data class MacroStep(
    val id: String = UUID.randomUUID().toString(),
    val actionType: ActionType = ActionType.TAP,
    // Normalized coordinates (0.0 to 1.0)
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.5f,
    val endNormalizedX: Float = 0.5f,
    val endNormalizedY: Float = 0.5f,
    // Timing parameters
    val durationMs: Long = 100L,
    val delayMs: Long = 300L,
    val jitterMs: Long = 20L,
    // Control flow parameters
    val loopCount: Int = 1, // 0 for infinite, N for fixed repetitions
    val label: String = ""
) {
    /**
     * Map normalized coordinates to device screen pixel coordinates.
     */
    fun getAbsoluteCoordinates(screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        val absX = (normalizedX * screenWidth).coerceIn(0f, screenWidth.toFloat())
        val absY = (normalizedY * screenHeight).coerceIn(0f, screenHeight.toFloat())
        return Pair(absX, absY)
    }

    /**
     * Map normalized end coordinates (for swipe gestures) to device screen pixels.
     */
    fun getAbsoluteEndCoordinates(screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        val absX = (endNormalizedX * screenWidth).coerceIn(0f, screenWidth.toFloat())
        val absY = (endNormalizedY * screenHeight).coerceIn(0f, screenHeight.toFloat())
        return Pair(absX, absY)
    }

    companion object {
        fun normalizePoint(absX: Float, absY: Float, screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
            val normX = if (screenWidth > 0) absX / screenWidth else 0f
            val normY = if (screenHeight > 0) absY / screenHeight else 0f
            return Pair(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
        }
    }
}

/**
 * Macro Script Profile schema representing a complete automation sequence.
 */
data class MacroScript(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val author: String = "Automator",
    val description: String = "",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val targetResolution: TargetResolution = TargetResolution(),
    val steps: List<MacroStep> = emptyList()
)
