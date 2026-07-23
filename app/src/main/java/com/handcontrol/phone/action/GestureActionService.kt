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
 * 无障碍服务 — 坐标点击操作抖音
 *
 * 使用精确屏幕比例坐标，不再依赖脆弱的无障碍树遍历。
 * 抖音右侧按钮列布局是固定的：点赞/评论/收藏/分享/头像，从上到下排列。
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

    // 抖音右侧按钮列坐标比例
    private val rightBtnX = 0.87f
    private val likeBtnY = 0.37f
    private val commentBtnY = 0.49f
    private val favoriteBtnY = 0.61f
    private val profileBtnY = 0.78f

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
            DouyinAction.COMMENT     -> tap(rightBtnX, commentBtnY)
            DouyinAction.FAVORITE    -> tap(rightBtnX, favoriteBtnY)
            DouyinAction.PROFILE     -> tap(rightBtnX, profileBtnY)
            DouyinAction.NONE        -> true
        }
    }

    private fun swipe(downward: Boolean): Boolean {
        val fromY = if (downward) screenHeight * 0.22f else screenHeight * 0.78f
        val toY   = if (downward) screenHeight * 0.78f else screenHeight * 0.22f
        val x = screenWidth * 0.5f
        val path = Path().apply { moveTo(x, fromY); lineTo(x, toY) }
        return dispatch(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build())
    }

    private fun tap(rx: Float, ry: Float): Boolean {
        val x = screenWidth * rx; val y = screenHeight * ry
        val path = Path().apply { moveTo(x, y) }
        return dispatch(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build())
    }

    private fun doubleTap(rx: Float, ry: Float): Boolean {
        tap(rx, ry)
        handler.postDelayed({ tap(rx, ry) }, 280)
        return true
    }

    private fun dispatch(gesture: GestureDescription): Boolean {
        val ok = dispatchGesture(gesture, null, null)
        if (!ok) Log.w(TAG, "dispatchGesture 失败")
        return ok
    }

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
