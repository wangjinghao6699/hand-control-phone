package com.handcontrol.phone.camera

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.view.WindowManager.LayoutParams.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.handcontrol.phone.MainActivity
import com.handcontrol.phone.R
import com.handcontrol.phone.action.GestureActionService
import com.handcontrol.phone.config.GestureMappingStore
import com.handcontrol.phone.gesture.DouyinAction
import com.handcontrol.phone.gesture.GestureDetector
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class OverlayService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "hand_control_overlay"
        private const val OVERLAY_WIDTH_DP = 150
        private const val OVERLAY_HEIGHT_DP = 200

        var isRunning = false
            private set
    }

    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var gestureTextView: TextView
    private lateinit var statusIndicator: View
    private lateinit var closeButton: View

    private var layoutParams: WindowManager.LayoutParams? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var mappingStore: GestureMappingStore

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override val lifecycle: LifecycleRegistry
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.CREATED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        gestureDetector = GestureDetector()
        mappingStore = GestureMappingStore(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        createOverlayView()
        setupCamera()

        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.STARTED
        Log.d(TAG, "悬浮窗服务创建完成")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        cameraManager.close()
        removeOverlayView()
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "手势控制", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "手势识别服务运行中" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("手势控制")
            .setContentText("摄像头手势识别运行中…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createOverlayView() {
        val density = resources.displayMetrics.density
        val widthPx = (OVERLAY_WIDTH_DP * density).roundToInt()
        val heightPx = (OVERLAY_HEIGHT_DP * density).roundToInt()

        overlayView = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.overlay_border)
            clipChildren = true
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        overlayView.addView(previewView)

        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(
                (4 * density).roundToInt(), (2 * density).roundToInt(),
                (4 * density).roundToInt(), (4 * density).roundToInt()
            )
        }
        statusBar.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }

        val indicator = View(this).apply { setBackgroundResource(R.drawable.circle_green) }
        indicator.layoutParams = LinearLayout.LayoutParams(
            (8 * density).roundToInt(), (8 * density).roundToInt()
        ).apply { setMargins(0, 0, 0, (2 * density).roundToInt()) }
        statusIndicator = indicator
        statusBar.addView(statusIndicator)

        gestureTextView = TextView(this).apply {
            text = "等待手势"
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            maxLines = 1
            setSingleLine(true)
        }
        statusBar.addView(gestureTextView)
        overlayView.addView(statusBar)

        closeButton = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CCFF0000"))
            layoutParams = FrameLayout.LayoutParams(
                (24 * density).roundToInt(), (24 * density).roundToInt()
            ).apply { gravity = Gravity.TOP or Gravity.END }
            setOnClickListener { stopSelf() }
        }
        overlayView.addView(closeButton)

        overlayView.setOnTouchListener { _, event -> handleTouch(event) }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            widthPx, heightPx, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).roundToInt()
            y = (120 * density).roundToInt()
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun removeOverlayView() {
        try {
            if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮窗失败: ${e.message}")
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams!!.x; initialY = layoutParams!!.y
                initialTouchX = event.rawX; initialTouchY = event.rawY
                isDragging = false; true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).roundToInt()
                val dy = (event.rawY - initialTouchY).roundToInt()
                if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                    isDragging = true
                    layoutParams!!.x = initialX - dx
                    layoutParams!!.y = initialY + dy
                    windowManager.updateViewLayout(overlayView, layoutParams)
                }
                true
            }
            MotionEvent.ACTION_UP -> !isDragging
            else -> false
        }
    }

    // ── 摄像头 + 手势识别 ──

    private fun setupCamera() {
        cameraManager = CameraManager(this, this)
        cameraManager.initHandLandmarker()

        scope.launch {
            gestureDetector.recordedTemplates = mappingStore.getAllTemplates()
            gestureDetector.resetEmaBuffer()
        }

        cameraManager.onHandLandmarksDetected = { result -> processHandResult(result) }
        previewView.post { cameraManager.startCamera(previewView) }
    }

    private fun processHandResult(result: HandLandmarkerResult?) {
        try {
            val landmarks = result?.landmarks()?.firstOrNull()
            val action = gestureDetector.processFrame(landmarks)
            scope.launch {
                updateGestureUI(action)
                if (action != DouyinAction.NONE) {
                    if (GestureActionService.execute(action)) flashIndicator()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "手势处理异常: ${e.message}", e)
        }
    }

    private fun updateGestureUI(action: DouyinAction) {
        if (action == DouyinAction.NONE) {
            gestureTextView.text = "等待手势"
            (statusIndicator.background as? GradientDrawable)?.setColor(
                ContextCompat.getColor(this, R.color.gesture_inactive))
        } else {
            gestureTextView.text = action.displayName
            (statusIndicator.background as? GradientDrawable)?.setColor(
                ContextCompat.getColor(this, R.color.gesture_active))
        }
    }

    private fun flashIndicator() {
        ValueAnimator.ofFloat(1f, 0.3f, 1f).apply {
            duration = 200
            addUpdateListener { statusIndicator.alpha = animatedValue as Float }
            start()
        }
    }

    fun updatePosition(x: Int, y: Int) {
        if (layoutParams != null) {
            layoutParams!!.x = x; layoutParams!!.y = y
            try { windowManager.updateViewLayout(overlayView, layoutParams) }
            catch (e: Exception) { Log.e(TAG, "更新位置失败: ${e.message}") }
        }
    }
}
