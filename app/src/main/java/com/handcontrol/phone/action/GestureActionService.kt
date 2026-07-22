package com.handcontrol.phone.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.handcontrol.phone.gesture.DouyinAction

/**
 * 无障碍服务 — 在抖音应用中模拟手势操作
 */
class GestureActionService : AccessibilityService() {

    companion object {
        private const val TAG = "GestureAction"
        private var instance: GestureActionService? = null

        fun execute(action: DouyinAction): Boolean {
            return instance?.performAction(action) ?: false
        }

        fun isRunning(): Boolean = instance != null
    }

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private val handler = Handler(Looper.getMainLooper())

    // 右侧按钮列 X 比例
    private val rightColumnX = 0.88f
    // 各按钮 Y 比例（抖音标准布局）
    private val likeY = 0.40f
    private val commentY = 0.52f
    private val favoriteY = 0.64f
    private val profileY = 0.80f

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
            DouyinAction.SWIPE_UP -> swipeVertical(false)
            DouyinAction.SWIPE_DOWN -> swipeVertical(true)
            DouyinAction.LIKE -> doubleTap(0.5f, 0.5f)          // 双击屏幕中央点赞
            DouyinAction.PAUSE -> tap(0.5f, 0.5f)               // 单击中央暂停
            DouyinAction.COMMENT -> tap(rightColumnX, commentY)  // 点击评论按钮
            DouyinAction.FAVORITE -> tap(rightColumnX, favoriteY)// 点击收藏按钮
            DouyinAction.PROFILE -> tap(rightColumnX, profileY)  // 点击作者头像
            DouyinAction.NONE -> true
        }
    }

    // ── 基础操作 ──

    private fun swipeVertical(downward: Boolean): Boolean {
        val fromY: Float
        val toY: Float
        if (downward) {
            fromY = screenHeight * 0.25f
            toY = screenHeight * 0.75f
        } else {
            fromY = screenHeight * 0.75f
            toY = screenHeight * 0.25f
        }
        val centerX = screenWidth * 0.5f

        val path = Path().apply {
            moveTo(centerX, fromY)
            lineTo(centerX, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatch(gesture)
    }

    private fun tap(rx: Float, ry: Float): Boolean {
        val x = screenWidth * rx
        val y = screenHeight * ry
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()
        return dispatch(gesture)
    }

    /** 双击 */
    private fun doubleTap(rx: Float, ry: Float): Boolean {
        tap(rx, ry)
        handler.postDelayed({
            tap(rx, ry)
        }, 150)
        return true
    }

    private fun dispatch(gesture: GestureDescription): Boolean {
        val result = dispatchGesture(gesture, null, null)
        if (!result) Log.w(TAG, "dispatchGesture 返回 false")
        return result
    }

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
