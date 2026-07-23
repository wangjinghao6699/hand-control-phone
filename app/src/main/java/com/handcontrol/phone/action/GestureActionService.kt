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
            DouyinAction.LIKE        -> clickByDesc("点赞")
            DouyinAction.PAUSE       -> tapCenter()
            DouyinAction.COMMENT     -> clickByDesc("评论")
            DouyinAction.FAVORITE    -> clickByDesc("收藏")
            DouyinAction.PROFILE     -> clickProfile()
            DouyinAction.NONE        -> true
        }
    }

    // ── 滑动 ──

    private fun swipe(downward: Boolean): Boolean {
        val fromY = if (downward) screenHeight * 0.22f else screenHeight * 0.78f
        val toY   = if (downward) screenHeight * 0.78f else screenHeight * 0.22f
        val x = screenWidth * 0.5f
        val path = Path().apply { moveTo(x, fromY); lineTo(x, toY) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build(), null, null)
    }

    private fun tapCenter(): Boolean {
        val x = screenWidth * 0.5f; val y = screenHeight * 0.5f
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build(), null, null)
    }

    // ── 核心：按 contentDescription 查找并点击 ──

    private fun clickByDesc(desc: String): Boolean {
        val root = rootInActiveWindow
        if (root == null) { Log.w(TAG, "rootInActiveWindow null, 回退坐标"); return fallbackCoord(desc) }

        val node = findByDesc(root, desc)
        root.recycle()

        if (node != null) {
            val rect = Rect(); node.getBoundsInScreen(rect)
            Log.d(TAG, "找到节点 desc=$desc bounds=$rect")
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!ok) {
                // 有些按钮的可点击性在父节点上
                val p = node.parent
                if (p != null) { val r = p.performAction(AccessibilityNodeInfo.ACTION_CLICK); p.recycle(); node.recycle(); return r }
            }
            node.recycle()
            if (ok) return true
        }

        Log.w(TAG, "未找到 desc='$desc'，回退坐标")
        return fallbackCoord(desc)
    }

    private fun findByDesc(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val cd = node.contentDescription?.toString() ?: ""
        if (cd.contains(desc) && (node.isClickable || hasClickableParent(node))) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findByDesc(child, desc)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun hasClickableParent(node: AccessibilityNodeInfo): Boolean {
        var p = node.parent
        while (p != null) {
            if (p.isClickable) { p.recycle(); return true }
            val next = p.parent; p.recycle(); p = next
        }
        return false
    }

    // ── 作者主页（右侧偏下的大头像区域） ──

    private fun clickProfile(): Boolean {
        val root = rootInActiveWindow ?: return fallbackCoord("profile")
        val node = findProfileNode(root)
        root.recycle()
        if (node != null) {
            Log.d(TAG, "点击作者头像")
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!ok) { val p = node.parent; if (p != null) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); p.recycle() } }
            node.recycle()
            if (ok) return true
        }
        return fallbackCoord("profile")
    }

    private fun findProfileNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val rect = Rect(); node.getBoundsInScreen(rect)
        // 作者头像：右侧偏下、较大尺寸(>50px)、可点击
        if (!rect.isEmpty && node.isClickable &&
            rect.left > screenWidth * 0.4f && rect.top > screenHeight * 0.55f &&
            rect.width() > 50 && rect.height() > 50) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findProfileNode(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    // ── 回退坐标 ──

    private fun fallbackCoord(hint: String): Boolean {
        val (rx, ry) = when (hint) {
            "点赞" -> 0.87f to 0.38f
            "评论" -> 0.87f to 0.49f
            "收藏" -> 0.87f to 0.61f
            "profile" -> 0.83f to 0.80f
            else -> 0.87f to 0.49f
        }
        val x = (screenWidth * rx).toInt(); val y = (screenHeight * ry).toInt()
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build(), null, null)
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
