package com.example

import com.example.io.MacroJsonManager
import com.example.model.ActionType
import com.example.model.MacroScript
import com.example.model.MacroStep
import com.example.model.TargetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MacroUnitTest {

    @Test
    fun testCoordinateNormalizationAndScaling() {
        val screenWidth = 1080
        val screenHeight = 2400

        // Test normal point (540, 1200) -> (0.5, 0.5)
        val (normX, normY) = MacroStep.normalizePoint(540f, 1200f, screenWidth, screenHeight)
        assertEquals(0.5f, normX, 0.001f)
        assertEquals(0.5f, normY, 0.001f)

        // Test reverse resolution mapping
        val step = MacroStep(
            actionType = ActionType.TAP,
            normalizedX = 0.25f,
            normalizedY = 0.75f
        )
        val (absX, absY) = step.getAbsoluteCoordinates(screenWidth, screenHeight)
        assertEquals(270f, absX, 0.1f)
        assertEquals(1800f, absY, 0.1f)
    }

    @Test
    fun testMacroJsonSerializationRoundTrip() {
        val originalScript = MacroScript(
            name = "Test Automation Loop",
            author = "Engineer",
            description = "Loop test with jitter delay",
            targetResolution = TargetResolution(1440, 3120),
            steps = listOf(
                MacroStep(actionType = ActionType.LOOP_START, loopCount = 3),
                MacroStep(
                    actionType = ActionType.TAP,
                    normalizedX = 0.33f,
                    normalizedY = 0.66f,
                    durationMs = 120L,
                    delayMs = 400L,
                    jitterMs = 30L
                ),
                MacroStep(
                    actionType = ActionType.SWIPE,
                    normalizedX = 0.1f,
                    normalizedY = 0.8f,
                    endNormalizedX = 0.9f,
                    endNormalizedY = 0.2f,
                    durationMs = 500L
                ),
                MacroStep(actionType = ActionType.LOOP_END)
            )
        )

        val json = MacroJsonManager.toJson(originalScript)
        assertTrue(json.contains("\"Test Automation Loop\""))
        assertTrue(json.contains("\"SWIPE\""))

        val deserializedResult = MacroJsonManager.fromJson(json)
        assertTrue(deserializedResult.isSuccess)

        val restored = deserializedResult.getOrThrow()
        assertEquals(originalScript.name, restored.name)
        assertEquals(originalScript.steps.size, restored.steps.size)
        assertEquals(ActionType.LOOP_START, restored.steps[0].actionType)
        assertEquals(ActionType.TAP, restored.steps[1].actionType)
        assertEquals(0.33f, restored.steps[1].normalizedX, 0.001f)
        assertEquals(ActionType.SWIPE, restored.steps[2].actionType)
        assertEquals(ActionType.LOOP_END, restored.steps[3].actionType)
    }
}
