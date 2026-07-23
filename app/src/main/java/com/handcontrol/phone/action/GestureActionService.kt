package com.handcontrol.phone.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.handcontrol.phone.gesture.DouyinAction

/**
 * 无障碍服务 — 双重策略操作抖音
 *
 * 策略1: dispatchGesture 模拟触摸（标准方法）
 * 策略2: Runtime.exec("input tap") shell 注入（兼容方法）
 */
class GestureActionService : AccessibilityService() {

    companion object {
        private const val TAG = "GestureAction"
        private var instance: GestureActionService? = null

        fun execute(action: DouyinAction): Boolean {
            return try {
                instance?.performAction(action) ?: false
            } catch (e: Exception) {
                Log.e(TAG, "执行异常: ${e.message}")
                false
            }
        }

        fun isRunning(): Boolean = instance != null
    }

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateScreenSize()
        Log.d(TAG, "无障碍服务已连接 — ${screenWidth}x${screenHeight}")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun performAction(action: DouyinAction): Boolean {
        updateScreenSize()
        Log.d(TAG, "执行: ${action.displayName}")

        return when (action) {
            DouyinAction.SWIPE_UP    -> swipe(false)
            DouyinAction.SWIPE_DOWN  -> swipe(true)
            DouyinAction.LIKE        -> doubleTap(0.5f, 0.5f)
            DouyinAction.PAUSE       -> tap(0.5f, 0.5f)
            DouyinAction.COMMENT     -> tap(0.87f, 0.49f)
            DouyinAction.FAVORITE    -> tap(0.87f, 0.61f)
            DouyinAction.PROFILE     -> tap(0.83f, 0.80f)
            DouyinAction.NONE        -> true
        }
    }

    // ── 策略1: dispatchGesture ──

    private fun swipe(downward: Boolean): Boolean {
        val fromY = if (downward) screenHeight * 0.22f else screenHeight * 0.78f
        val toY   = if (downward) screenHeight * 0.78f else screenHeight * 0.22f
        val x = screenWidth * 0.5f
        val path = Path().apply { moveTo(x, fromY); lineTo(x, toY) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
                .build(), callback(), null
        )
    }

    private fun tap(rx: Float, ry: Float): Boolean {
        val x = (screenWidth * rx).toInt()
        val y = (screenHeight * ry).toInt()

        // 策略1: dispatchGesture
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val ok = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build(), null, null
        )
        if (!ok) {
            Log.w(TAG, "dispatchGesture 失败 ($x,$y)，尝试 shell")
            // 策略2: shell input tap 回退
            return shellTap(x, y)
        }
        return true
    }

    private fun doubleTap(rx: Float, ry: Float): Boolean {
        tap(rx, ry)
        handler.postDelayed({ tap(rx, ry) }, 280)
        return true
    }

    private fun callback() = object : GestureDescription.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            Log.d(TAG, "手势完成")
        }
        override fun onCancelled(gestureDescription: GestureDescription?) {
            Log.w(TAG, "手势取消")
        }
    }

    // ── 策略2: shell input tap（回退方案） ──

    private fun shellTap(x: Int, y: Int): Boolean {
        return try {
            val cmd = "input tap $x $y"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val exitCode = process.waitFor()
            Log.d(TAG, "shell tap ($x,$y) exit=$exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "shell tap 异常: ${e.message}")
            false
        }
    }

    // ── 屏幕尺寸 ──

    private fun updateScreenSize() {
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width(); screenHeight = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels
        }
    }
}
