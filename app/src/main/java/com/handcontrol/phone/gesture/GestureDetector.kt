package com.handcontrol.phone.gesture

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.handcontrol.phone.config.GestureMappingStore

/**
 * 手势检测器 — 纯特征匹配
 *
 * 所有操作（包括滑动）都通过录制的手指状态特征匹配来识别。
 * 每帧提取 5 根手指的伸直/弯曲状态，与已录制特征做 5 分制匹配。
 */
class GestureDetector(
    private val holdFrames: Int = 8,
    private val cooldownFrames: Int = 25,
    private val matchThreshold: Int = 4      // 匹配度阈值 (满分 5)
) {
    var recordedProfiles: Map<DouyinAction, Int> = emptyMap()

    private var lastStableAction: DouyinAction = DouyinAction.NONE
    private var stableFrameCount: Int = 0
    private var cooldownRemaining: Int = 0

    fun processFrame(landmarks: List<NormalizedLandmark>?): DouyinAction {
        if (cooldownRemaining > 0) {
            cooldownRemaining--
            return DouyinAction.NONE
        }

        if (landmarks.isNullOrEmpty() || landmarks.size < 21) {
            resetState()
            return DouyinAction.NONE
        }

        val fingerState = HandLandmarkHelper.getFingerStates(landmarks)
        val matchedAction = matchProfile(fingerState)

        return if (matchedAction == DouyinAction.NONE) {
            resetState()
            DouyinAction.NONE
        } else {
            stabilizeAction(matchedAction)
        }
    }

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

    private fun triggerAction(action: DouyinAction): DouyinAction {
        cooldownRemaining = cooldownFrames
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
