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

/**
 * 无障碍服务 — 通过 AccessibilityNodeInfo 树遍历 + dispatchGesture 操作抖音
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

    // ──────────────────────────────────────────
    // 入口
    // ──────────────────────────────────────────

    fun performAction(action: DouyinAction): Boolean {
        updateScreenSize()
        Log.d(TAG, "执行: ${action.displayName}")

        return when (action) {
            DouyinAction.SWIPE_UP    -> swipeVertical(false)
            DouyinAction.SWIPE_DOWN  -> swipeVertical(true)
            DouyinAction.LIKE        -> doubleTapCenter()
            DouyinAction.PAUSE       -> tapCenter()
            DouyinAction.COMMENT     -> clickRightPanelButton(targetIndex = 1) // 评论是右侧第2个
            DouyinAction.FAVORITE    -> clickRightPanelButton(targetIndex = 2) // 收藏是右侧第3个
            DouyinAction.PROFILE     -> clickProfileButton()                   // 作者头像特殊处理
            DouyinAction.NONE        -> true
        }
    }

    // ──────────────────────────────────────────
    // 滑动
    // ──────────────────────────────────────────

    private fun swipeVertical(downward: Boolean): Boolean {
        val fromY = if (downward) screenHeight * 0.2f else screenHeight * 0.8f
        val toY   = if (downward) screenHeight * 0.8f else screenHeight * 0.2f
        val centerX = screenWidth * 0.5f

        val path = Path().apply {
            moveTo(centerX, fromY)
            lineTo(centerX, toY)
        }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
                .build(),
            null, null
        )
    }

    // ──────────────────────────────────────────
    // 双击点赞
    // ──────────────────────────────────────────

    private fun doubleTapCenter(): Boolean {
        tapCenter()
        handler.postDelayed({ tapCenter() }, 300) // 标准双击间隔 300ms
        return true
    }

    private fun tapCenter(): Boolean {
        val x = screenWidth * 0.5f
        val y = screenHeight * 0.5f
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                .build(),
            null, null
        )
    }

    // ──────────────────────────────────────────
    // AccessibilityNodeInfo 树遍历 — 核心方法
    // ──────────────────────────────────────────

    /**
     * 遍历无障碍节点树，找到右侧面板中从上往下第 targetIndex 个可点击按钮并点击
     *
     * 抖音右侧面板按钮排列（从上到下）：
     *   0: 点赞 (爱心)
     *   1: 评论
     *   2: 收藏
     *   3: 分享
     *   ... 下方是作者头像
     */
    private fun clickRightPanelButton(targetIndex: Int): Boolean {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "rootInActiveWindow 为 null，回退到坐标点击")
            return fallbackCoordTap(targetIndex)
        }

        val clickables = mutableListOf<AccessibilityNodeInfo>()
        collectClickables(root, clickables)
        root.recycle()

        // 筛选：位于屏幕右侧 30% 区域的可点击元素
        val rightPanel = clickables
            .filter { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.left > screenWidth * 0.65f && !rect.isEmpty
            }
            .sortedBy { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.top
            }
            .distinctBy { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.centerY() / 20 * 20 // 去重：Y 坐标相差 <20px 的视为同一行
            }

        Log.d(TAG, "右侧面板找到 ${rightPanel.size} 个按钮，目标索引 $targetIndex")

        val success = if (targetIndex < rightPanel.size) {
            val node = rightPanel[targetIndex]
            val rect = Rect()
            node.getBoundsInScreen(rect)
            Log.d(TAG, "点击节点: bounds=$rect, text=${node.text}, desc=${node.contentDescription}")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            Log.w(TAG, "索引 $targetIndex 超出范围 (共 ${rightPanel.size} 个)，回退坐标点击")
            false
        }

        // 回收节点
        clickables.forEach { it.recycle() }
        rightPanel.forEach { it.recycle() }

        return if (success) true else fallbackCoordTap(targetIndex)
    }

    /** 点击作者头像（右侧面板最下方的大头像） */
    private fun clickProfileButton(): Boolean {
        val root = rootInActiveWindow ?: return fallbackProfileTap()

        val clickables = mutableListOf<AccessibilityNodeInfo>()
        collectClickables(root, clickables)
        root.recycle()

        // 作者头像通常较大且在右侧偏下位置
        val profileCandidates = clickables
            .filter { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                !rect.isEmpty &&
                rect.left > screenWidth * 0.5f &&
                rect.top > screenHeight * 0.65f &&
                rect.width() > 40 && rect.height() > 40 // 头像通常 >40px
            }
            .sortedBy { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.top
            }

        val success = if (profileCandidates.isNotEmpty()) {
            val node = profileCandidates.first()
            Log.d(TAG, "点击作者头像: bounds=${Rect().also { node.getBoundsInScreen(it) }}")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else false

        clickables.forEach { it.recycle() }
        profileCandidates.forEach { it.recycle() }

        return if (success) true else fallbackProfileTap()
    }

    // ──────────────────────────────────────────
    // 递归收集可点击节点
    // ──────────────────────────────────────────

    private fun collectClickables(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickables(child, out)
            child.recycle()
        }
    }

    // ──────────────────────────────────────────
    // 回退方案：坐标点击（当无障碍树不可用时）
    // ──────────────────────────────────────────

    private fun fallbackCoordTap(index: Int): Boolean {
        val rx = 0.88f
        val ry = when (index) {
            0 -> 0.38f  // 点赞
            1 -> 0.50f  // 评论
            2 -> 0.62f  // 收藏
            else -> 0.38f
        }
        val path = Path().apply { moveTo(screenWidth * rx, screenHeight * ry) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                .build(),
            null, null
        )
    }

    private fun fallbackProfileTap(): Boolean {
        val path = Path().apply { moveTo(screenWidth * 0.88f, screenHeight * 0.78f) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                .build(),
            null, null
        )
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
