package com.handcontrol.phone.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.handcontrol.phone.gesture.DouyinAction

class GestureActionService : AccessibilityService() {

    companion object {
        private const val TAG = "GestureAction"
        private var instance: GestureActionService? = null
        fun execute(action: DouyinAction): Boolean = try { instance?.performAction(action) ?: false } catch (e: Exception) { false }
        fun isRunning(): Boolean = instance != null
    }

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { super.onServiceConnected(); instance = this; updateScreenSize() }
    override fun onDestroy() { super.onDestroy(); instance = null }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun performAction(action: DouyinAction): Boolean {
        updateScreenSize()
        Log.d(TAG, "执行: ${action.displayName}")
        return when (action) {
            DouyinAction.SWIPE_UP    -> swipe(false)
            DouyinAction.SWIPE_DOWN  -> swipe(true)
            DouyinAction.LIKE        -> doubleTapCenter()
            DouyinAction.PAUSE       -> tapCenter()
            DouyinAction.COMMENT     -> clickNear(0.87f, 0.49f)
            DouyinAction.FAVORITE    -> clickNear(0.87f, 0.61f)
            DouyinAction.PROFILE     -> clickNear(0.83f, 0.80f)
            DouyinAction.NONE        -> true
        }
    }

    private fun swipe(downward: Boolean): Boolean {
        val fromY = if (downward) screenHeight * 0.22f else screenHeight * 0.78f
        val toY   = if (downward) screenHeight * 0.78f else screenHeight * 0.22f
        val x = screenWidth * 0.5f
        val path = Path().apply { moveTo(x, fromY); lineTo(x, toY) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build(), null, null)
    }

    private fun tapCenter(): Boolean = clickNear(0.5f, 0.5f)

    private fun doubleTapCenter(): Boolean { tapCenter(); handler.postDelayed({ tapCenter() }, 280); return true }

    private fun clickNear(rx: Float, ry: Float): Boolean {
        val tx = (screenWidth * rx).toInt(); val ty = (screenHeight * ry).toInt()
        val root = rootInActiveWindow
        if (root != null) {
            val target = findClickableNear(root, tx, ty, 60)
            root.recycle()
            if (target != null) {
                val rect = Rect(); target.getBoundsInScreen(rect)
                Log.d(TAG, "节点点击: $rect, text=${target.text}, desc=${target.contentDescription}")
                val ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!ok) {
                    val p = target.parent; if (p != null) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); p.recycle() }
                }
                target.recycle()
                if (ok) return true
            }
        }
        // fallback
        Log.d(TAG, "回退 dispatchGesture ($tx,$ty)")
        val path = Path().apply { moveTo(tx.toFloat(), ty.toFloat()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build(), null, null)
    }

    private fun findClickableNear(node: AccessibilityNodeInfo, tx: Int, ty: Int, tolerance: Int): AccessibilityNodeInfo? {
        val rect = Rect(); node.getBoundsInScreen(rect)
        if (!rect.isEmpty && node.isClickable && tx >= rect.left - tolerance && tx <= rect.right + tolerance && ty >= rect.top - tolerance && ty <= rect.bottom + tolerance)
            return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val r = findClickableNear(child, tx, ty, tolerance); child.recycle()
            if (r != null) return r
        }
        return null
    }

    private fun updateScreenSize() {
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds; screenWidth = bounds.width(); screenHeight = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels
        }
    }
}
