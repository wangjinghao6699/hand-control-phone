package com.handcontrol.phone.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.handcontrol.phone.gesture.DouyinAction

/**
 * 无障碍服务 — 在抖音应用中模拟手势操作
 *
 * 需要在系统设置中手动授权：设置 → 无障碍 → 手势控制
 */
class GestureActionService : AccessibilityService() {

    companion object {
        private const val TAG = "GestureAction"
        private var instance: GestureActionService? = null

        /** 外部调用入口：发送操作指令 */
        fun execute(action: DouyinAction): Boolean {
            return instance?.performAction(action) ?: false
        }

        /** 检查无障碍服务是否已启用 */
        fun isRunning(): Boolean = instance != null
    }

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    // ──────────────────────────────────────────
    // 相对坐标（占屏幕百分比）
    // 这些值对应抖音的标准布局中的按钮位置
    // ──────────────────────────────────────────

    /** 右侧按钮列的 X 偏移（距右边缘的相对比例） */
    private val rightColumnXRatio = 0.88f

    /** 各按钮的 Y 偏移比例 */
    private val likeYRatio = 0.38f        // 点赞
    private val commentYRatio = 0.50f      // 评论区
    private val favoriteYRatio = 0.62f     // 收藏
    private val profileYRatio = 0.78f      // 作者头像

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateScreenSize()
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "无障碍服务已断开")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理事件，仅使用手势模拟功能
    }

    override fun onInterrupt() {}

    // ──────────────────────────────────────────
    // 操作执行
    // ──────────────────────────────────────────

    fun performAction(action: DouyinAction): Boolean {
        updateScreenSize()
        return when (action) {
            DouyinAction.SWIPE_UP -> swipeVertical(fromTop = false)
            DouyinAction.SWIPE_DOWN -> swipeVertical(fromTop = true)
            DouyinAction.LIKE -> clickAt(relativeX = rightColumnXRatio, relativeY = likeYRatio)
            DouyinAction.FAVORITE -> clickAt(relativeX = rightColumnXRatio, relativeY = favoriteYRatio)
            DouyinAction.COMMENT -> clickAt(relativeX = rightColumnXRatio, relativeY = commentYRatio)
            DouyinAction.PROFILE -> clickAt(relativeX = rightColumnXRatio, relativeY = profileYRatio)
            DouyinAction.PAUSE -> longPressAt(relativeX = 0.5f, relativeY = 0.5f)
            DouyinAction.NONE -> true
        }
    }

    // ──────────────────────────────────────────
    // 底层手势 API
    // ──────────────────────────────────────────

    /** 垂直滑动 */
    private fun swipeVertical(fromTop: Boolean): Boolean {
        val startY: Float
        val endY: Float
        if (fromTop) {
            // 从上往下划
            startY = screenHeight * 0.3f
            endY = screenHeight * 0.7f
        } else {
            // 从下往上划
            startY = screenHeight * 0.7f
            endY = screenHeight * 0.3f
        }
        val centerX = screenWidth * 0.5f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        val result = dispatchGesture(gesture, null, null)
        Log.d(TAG, "滑动 ${if (fromTop) "向下" else "向上"}: $result")
        return result
    }

    /** 点击指定相对坐标 */
    private fun clickAt(relativeX: Float, relativeY: Float): Boolean {
        val x = screenWidth * relativeX
        val y = screenHeight * relativeY
        return tap(x, y)
    }

    /** 单点点击 */
    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()

        val result = dispatchGesture(gesture, null, null)
        Log.d(TAG, "点击 ($x, $y): $result")
        return result
    }

    /** 长按 */
    private fun longPressAt(relativeX: Float, relativeY: Float): Boolean {
        val x = screenWidth * relativeX
        val y = screenHeight * relativeY

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 800))
            .build()

        val result = dispatchGesture(gesture, null, null)
        Log.d(TAG, "长按 ($x, $y): $result")
        return result
    }

    // ──────────────────────────────────────────
    // 屏幕尺寸
    // ──────────────────────────────────────────

    private fun updateScreenSize() {
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }
}
