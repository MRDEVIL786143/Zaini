package com.example.engine

import android.util.Log
import com.example.model.ActionType
import com.example.model.MacroScript
import com.example.model.MacroStep
import com.example.service.MacroAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * State representing playback lifecycle.
 */
sealed class ExecutionState {
    object Idle : ExecutionState()
    data class Running(val stepIndex: Int, val totalSteps: Int, val currentStep: MacroStep) : ExecutionState()
    data class Paused(val stepIndex: Int) : ExecutionState()
    object Finished : ExecutionState()
    object Stopped : ExecutionState()
    data class Error(val message: String) : ExecutionState()
}

/**
 * Stack frame tracking iteration count for nested or top-level loops.
 */
data class LoopFrame(
    val loopStartIndex: Int,
    val targetCount: Int, // 0 indicates infinite
    var currentIteration: Int = 1
)

/**
 * High-precision macro execution engine handling gesture injection,
 * stack-based control-flow loops, variable delay jitter, and drift compensation.
 */
class MacroExecutionEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "MacroExecutionEngine"
    }

    private val _executionState = MutableStateFlow<ExecutionState>(ExecutionState.Idle)
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()

    private var playbackJob: Job? = null
    private val pauseMutex = Mutex()
    @Volatile
    private var isPaused = false

    /**
     * Start execution of a macro script.
     *
     * @param script Macro profile to execute
     * @param screenWidth Current device display width in pixels
     * @param screenHeight Current device display height in pixels
     */
    fun startExecution(script: MacroScript, screenWidth: Int, screenHeight: Int) {
        if (!MacroAccessibilityService.isConnected()) {
            _executionState.value = ExecutionState.Error("Accessibility Service is not enabled. Please enable it in Settings.")
            return
        }

        if (script.steps.isEmpty()) {
            _executionState.value = ExecutionState.Error("Macro script contains no steps.")
            return
        }

        stopExecution()
        isPaused = false

        playbackJob = scope.launch {
            try {
                executeScriptInternal(script, screenWidth, screenHeight)
            } catch (e: CancellationException) {
                Log.d(TAG, "Macro execution cancelled.")
                _executionState.value = ExecutionState.Stopped
            } catch (e: Exception) {
                Log.e(TAG, "Error during macro execution", e)
                _executionState.value = ExecutionState.Error(e.localizedMessage ?: "Unknown execution error")
            }
        }
    }

    /**
     * Pause execution at current step.
     */
    fun pauseExecution() {
        if (_executionState.value is ExecutionState.Running) {
            isPaused = true
            val current = _executionState.value as ExecutionState.Running
            _executionState.value = ExecutionState.Paused(current.stepIndex)
            Log.d(TAG, "Macro execution paused.")
        }
    }

    /**
     * Resume paused macro execution.
     */
    fun resumeExecution() {
        if (isPaused) {
            isPaused = false
            Log.d(TAG, "Macro execution resumed.")
        }
    }

    /**
     * Stop and reset playback engine.
     */
    fun stopExecution() {
        playbackJob?.cancel()
        playbackJob = null
        isPaused = false
        _executionState.value = ExecutionState.Stopped
    }

    private suspend fun executeScriptInternal(
        script: MacroScript,
        screenWidth: Int,
        screenHeight: Int
    ) {
        val steps = script.steps
        val loopStack = ArrayDeque<LoopFrame>()
        var pc = 0 // Program counter

        while (pc in steps.indices) {
            // Check pause state
            while (isPaused) {
                delay(50)
            }

            val step = steps[pc]
            _executionState.value = ExecutionState.Running(pc, steps.size, step)

            when (step.actionType) {
                ActionType.LOOP_START -> {
                    // Push loop frame onto execution stack
                    loopStack.addLast(
                        LoopFrame(
                            loopStartIndex = pc,
                            targetCount = step.loopCount,
                            currentIteration = 1
                        )
                    )
                    pc++
                }

                ActionType.LOOP_END -> {
                    if (loopStack.isNotEmpty()) {
                        val currentLoop = loopStack.last()
                        val isInfinite = currentLoop.targetCount <= 0
                        val hasRemainingIterations = currentLoop.currentIteration < currentLoop.targetCount

                        if (isInfinite || hasRemainingIterations) {
                            currentLoop.currentIteration++
                            // Jump back to the instruction immediately following LOOP_START
                            pc = currentLoop.loopStartIndex + 1
                        } else {
                            // Loop finished; pop and proceed
                            loopStack.removeLast()
                            pc++
                        }
                    } else {
                        // Unmatched LOOP_END, continue sequentially
                        Log.w(TAG, "Unmatched LOOP_END encountered at PC=$pc")
                        pc++
                    }
                }

                ActionType.TAP -> {
                    val (x, y) = step.getAbsoluteCoordinates(screenWidth, screenHeight)
                    val gesture = MacroAccessibilityService.buildTapGesture(x, y, step.durationMs)
                    val success = MacroAccessibilityService.dispatchGestureAwait(gesture)
                    if (!success) {
                        Log.w(TAG, "Tap dispatch returned false at step $pc ($x, $y)")
                    }
                    applyPrecisionDelayWithJitter(step.delayMs, step.jitterMs)
                    pc++
                }

                ActionType.SWIPE -> {
                    val (x1, y1) = step.getAbsoluteCoordinates(screenWidth, screenHeight)
                    val (x2, y2) = step.getAbsoluteEndCoordinates(screenWidth, screenHeight)
                    val gesture = MacroAccessibilityService.buildSwipeGesture(x1, y1, x2, y2, step.durationMs)
                    val success = MacroAccessibilityService.dispatchGestureAwait(gesture)
                    if (!success) {
                        Log.w(TAG, "Swipe dispatch returned false at step $pc ($x1,$y1)->($x2,$y2)")
                    }
                    applyPrecisionDelayWithJitter(step.delayMs, step.jitterMs)
                    pc++
                }

                ActionType.DELAY -> {
                    applyPrecisionDelayWithJitter(step.delayMs, step.jitterMs)
                    pc++
                }
            }
        }

        _executionState.value = ExecutionState.Finished
        Log.i(TAG, "Macro execution finished successfully.")
    }

    /**
     * High precision monotonic delay mechanism with anti-bot randomized jitter.
     * Prevents timing drift across repeated executions.
     */
    private suspend fun applyPrecisionDelayWithJitter(baseDelayMs: Long, jitterMs: Long) {
        val jitter = if (jitterMs > 0) Random.nextLong(-jitterMs, jitterMs + 1) else 0L
        val effectiveDelayMs = (baseDelayMs + jitter).coerceAtLeast(0L)
        if (effectiveDelayMs <= 0) return

        val targetNanos = System.nanoTime() + (effectiveDelayMs * 1_000_000L)

        // For large intervals, sleep cooperatively to save battery
        if (effectiveDelayMs > 15) {
            delay(effectiveDelayMs - 10)
        }

        // Precision spin-wait for the remaining sub-millisecond tail
        while (System.nanoTime() < targetNanos) {
            // High-resolution monotonic wait
            Thread.yield()
        }
    }
}
