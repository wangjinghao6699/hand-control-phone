package com.handcontrol.phone.gesture

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.handcontrol.phone.config.GestureMappingStore

/**
 * 手势检测器 — 录制特征匹配 + 滑动检测
 *
 * 识别流程：
 * 1. 检测动态手势（滑动） → SWIPE_UP / SWIPE_DOWN
 * 2. 提取手指状态 → 与已录制特征匹配 → 返回对应的 DouyinAction
 * 3. 防抖 + 冷却机制
 */
class GestureDetector(
    private val holdFrames: Int = 8,
    private val cooldownFrames: Int = 25,
    private val swipeThreshold: Float = 0.06f,
    private val matchThreshold: Int = 4      // 匹配度阈值 (满分 5，至少 4 分)
) {
    // 已录制的手势特征 (操作 → 手指状态编码)，由外部设置
    var recordedProfiles: Map<DouyinAction, Int> = emptyMap()

    private var lastStableAction: DouyinAction = DouyinAction.NONE
    private var stableFrameCount: Int = 0
    private var cooldownRemaining: Int = 0

    // 滑动检测
    private var wristYHistory = ArrayDeque<Float>(5)

    fun processFrame(landmarks: List<NormalizedLandmark>?): DouyinAction {
        if (cooldownRemaining > 0) {
            cooldownRemaining--
            return DouyinAction.NONE
        }

        if (landmarks.isNullOrEmpty() || landmarks.size < 21) {
            resetState()
            return DouyinAction.NONE
        }

        // 1. 动态手势：滑动
        val swipeAction = detectSwipe(landmarks)
        if (swipeAction != DouyinAction.NONE) {
            return triggerAction(swipeAction)
        }

        // 2. 静态手势：特征匹配
        val fingerState = HandLandmarkHelper.getFingerStates(landmarks)
        val matchedAction = matchProfile(fingerState)

        return if (matchedAction == DouyinAction.NONE) {
            resetState()
            DouyinAction.NONE
        } else {
            stabilizeAction(matchedAction)
        }
    }

    // ── 特征匹配 ──

    private fun matchProfile(state: HandLandmarkHelper.FingerState): DouyinAction {
        if (recordedProfiles.isEmpty()) return DouyinAction.NONE

        var bestAction = DouyinAction.NONE
        var bestScore = 0

        for ((action, code) in recordedProfiles) {
            val recordedState = GestureMappingStore.decodeFingerState(code)
            val score = GestureMappingStore.matchScore(state, recordedState)
            if (score > bestScore) {
                bestScore = score
                bestAction = action
            }
        }

        return if (bestScore >= matchThreshold) bestAction else DouyinAction.NONE
    }

    // ── 防抖 ──

    private fun stabilizeAction(action: DouyinAction): DouyinAction {
        return if (action == lastStableAction) {
            stableFrameCount++
            if (stableFrameCount >= holdFrames) {
                stableFrameCount = 0
                action
            } else {
                DouyinAction.NONE
            }
        } else {
            lastStableAction = action
            stableFrameCount = 1
            DouyinAction.NONE
        }
    }

    // ── 滑动 ──

    private fun detectSwipe(landmarks: List<NormalizedLandmark>): DouyinAction {
        val wristY = landmarks[HandLandmarkHelper.WRIST].y()
        wristYHistory.addLast(wristY)
        if (wristYHistory.size > 5) wristYHistory.removeFirst()
        if (wristYHistory.size < 5) return DouyinAction.NONE

        val deltaY = wristYHistory.last() - wristYHistory.first()
        return when {
            deltaY < -swipeThreshold -> DouyinAction.SWIPE_UP
            deltaY > swipeThreshold -> DouyinAction.SWIPE_DOWN
            else -> DouyinAction.NONE
        }
    }

    // ── 触发 + 冷却 ──

    private fun triggerAction(action: DouyinAction): DouyinAction {
        cooldownRemaining = cooldownFrames
        wristYHistory.clear()
        resetState()
        Log.d("GestureDetector", "触发: ${action.displayName}")
        return action
    }

    private fun resetState() {
        lastStableAction = DouyinAction.NONE
        stableFrameCount = 0
    }

    fun getDebugInfo(landmarks: List<NormalizedLandmark>?): String {
        if (landmarks.isNullOrEmpty()) return "未检测到手部"
        val state = HandLandmarkHelper.getFingerStates(landmarks)
        return "拇指:${b(state.thumbExtended)} 食指:${b(state.indexExtended)} " +
                "中指:${b(state.middleExtended)} 无名指:${b(state.ringExtended)} " +
                "小指:${b(state.pinkyExtended)} | 冷却:$cooldownRemaining | 已录制:${recordedProfiles.size}个"
    }

    private fun b(v: Boolean) = if (v) "伸" else "曲"
}
