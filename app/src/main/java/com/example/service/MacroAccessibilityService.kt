package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Core Accessibility Service responsible for input gesture injection
 * and window state observation.
 */
class MacroAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MacroAccessibility"

        @Volatile
        private var instance: MacroAccessibilityService? = null

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        fun isConnected(): Boolean = instance != null

        fun getInstance(): MacroAccessibilityService? = instance

        /**
         * Suspend function to dispatch a gesture and await its completion.
         */
        suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean {
            val service = instance ?: run {
                Log.e(TAG, "AccessibilityService instance is null! Cannot dispatch gesture.")
                return false
            }

            return suspendCancellableCoroutine { continuation ->
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Gesture completed successfully.")
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "Gesture was cancelled by system.")
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }

                val dispatched = service.dispatchGesture(gesture, callback, null)
                if (!dispatched) {
                    Log.e(TAG, "Failed to schedule gesture dispatch.")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
        }

        /**
         * Construct a tap gesture description.
         */
        fun buildTapGesture(x: Float, y: Float, durationMs: Long = 100L): GestureDescription {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(10L))
            return GestureDescription.Builder().addStroke(stroke).build()
        }

        /**
         * Construct a swipe gesture description.
         */
        fun buildSwipeGesture(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long = 300L
        ): GestureDescription {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50L))
            return GestureDescription.Builder().addStroke(stroke).build()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.i(TAG, "MacroAccessibilityService connected and ready for gesture dispatch.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observes view focus, clicks, or window state changes for conditional evaluation
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "Active Window Changed: ${event.packageName} / ${event.className}")
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.d(TAG, "View Clicked: ${event.packageName} class=${event.className}")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "MacroAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceActive.value = false
        }
        Log.i(TAG, "MacroAccessibilityService destroyed.")
    }
}
