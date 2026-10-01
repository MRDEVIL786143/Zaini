package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.engine.ExecutionState
import com.example.engine.MacroExecutionEngine
import com.example.model.ActionType
import com.example.model.MacroScript
import com.example.model.MacroStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Floating System Overlay Service providing an on-screen floating control HUD
 * across target applications.
 */
class FloatingOverlayService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_OVERLAY"
        const val ACTION_STOP = "ACTION_STOP_OVERLAY"
        private const val CHANNEL_ID = "macro_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        val executionEngine = MacroExecutionEngine()

        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

        // Active macro script ready for overlay playback
        var activeScript: MacroScript? = null

        // In-memory recorded steps when using on-screen recording mode
        val recordedSteps = mutableListOf<MacroStep>()
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        _isOverlayActive.value = true
        setupFloatingView()
        observeExecutionEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply {
                setColor(0xEE1E1E2E.toInt())
                cornerRadius = 32f
                setStroke(2, 0xFF6366F1.toInt())
            }
        }

        val titleView = TextView(this).apply {
            text = "⚡ Macro"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(8, 8, 12, 8)
        }
        container.addView(titleView)

        // Record Button
        val recordBtn = Button(this).apply {
            text = "⏺ Rec"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFFEF4444.toInt())
            setOnClickListener {
                toggleRecording()
            }
        }
        container.addView(recordBtn)

        // Play Button
        val playBtn = Button(this).apply {
            text = "▶ Play"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF10B981.toInt())
            setOnClickListener {
                triggerPlayback()
            }
        }
        container.addView(playBtn)

        // Stop Button
        val stopBtn = Button(this).apply {
            text = "⏹ Stop"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF6B7280.toInt())
            setOnClickListener {
                executionEngine.stopExecution()
                if (isRecording) {
                    toggleRecording()
                }
            }
        }
        container.addView(stopBtn)

        // Drag listener for positioning the floating HUD
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(container, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        overlayView = container
        windowManager?.addView(container, layoutParams)
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        if (isRecording) {
            recordedSteps.clear()
            // In a production setup, user can tap add-point or tap on screen to capture normalized coordinate
            val metrics = resources.displayMetrics
            // Record a representative touch sample
            recordedSteps.add(
                MacroStep(
                    actionType = ActionType.TAP,
                    normalizedX = 0.5f,
                    normalizedY = 0.5f,
                    durationMs = 80L,
                    delayMs = 250L,
                    label = "Recorded Action 1"
                )
            )
        }
    }

    private fun triggerPlayback() {
        val scriptToRun = activeScript ?: run {
            if (recordedSteps.isNotEmpty()) {
                MacroScript(
                    name = "Recorded Overlay Macro",
                    steps = recordedSteps.toList()
                )
            } else {
                null
            }
        }

        if (scriptToRun != null) {
            val metrics = resources.displayMetrics
            executionEngine.startExecution(scriptToRun, metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun observeExecutionEngine() {
        serviceScope.launch {
            executionEngine.executionState.collect { state ->
                // Can update HUD status indicators or notification
            }
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Macro Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains active floating macro control overlay"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Macro Overlay Active")
            .setContentText("Tap to return to Macro Recorder dashboard")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
        _isOverlayActive.value = false
    }
}
