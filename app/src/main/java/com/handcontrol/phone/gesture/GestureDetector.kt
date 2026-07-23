package com.handcontrol.phone.gesture

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * 手势检测器 — 归一化 MSE 匹配 + EMA 平滑
 *
 * 参照 hand-control-dy 算法：
 * 1. EMA 平滑 21 个关键点
 * 2. 归一化（手腕原点 + 中掌指缩放）
 * 3. 与已录制模板做 MSE 匹配
 * 4. 连续 N 帧最小 MSE 低于阈值 + 冷却 → 触发
 */
class GestureDetector(
    private val holdFrames: Int = 10,           // 连续匹配帧数
    private val cooldownFrames: Int = 50,       // 冷却帧数 (~3s)
    private val mseThreshold: Float = 0.04f     // MSE 阈值（越大越宽松，0.02-0.06 推荐）
) {
    var recordedTemplates: Map<DouyinAction, FloatArray> = emptyMap()

    private val emaBuffer = HandLandmarkHelper.SmoothedLandmarks()
    private var lastMatchedAction: DouyinAction = DouyinAction.NONE
    private var stableCount: Int = 0
    private var cooldownRemaining: Int = 0
    private var lastTriggeredAction: DouyinAction = DouyinAction.NONE

    fun processFrame(landmarks: List<NormalizedLandmark>?): DouyinAction {
        if (cooldownRemaining > 0) {
            cooldownRemaining--
            return DouyinAction.NONE
        }

        if (landmarks.isNullOrEmpty() || landmarks.size < 21) {
            resetState()
            return DouyinAction.NONE
        }

        // 1. EMA 平滑
        HandLandmarkHelper.smooth(landmarks, emaBuffer)
        if (!emaBuffer.initialized) return DouyinAction.NONE

        // 2. 归一化
        val normalized = HandLandmarkHelper.normalize(emaBuffer.values)

        // 3. MSE 匹配
        val matched = matchByMSE(normalized)

        return if (matched == DouyinAction.NONE) {
            resetState()
            DouyinAction.NONE
        } else {
            stabilize(matched)
        }
    }

    private fun matchByMSE(normalized: FloatArray): DouyinAction {
        if (recordedTemplates.isEmpty()) return DouyinAction.NONE

        var bestAction = DouyinAction.NONE
        var bestMse = Float.MAX_VALUE

        for ((action, template) in recordedTemplates) {
            val err = HandLandmarkHelper.mse(normalized, template)
            if (err < bestMse) {
                bestMse = err
                bestAction = action
            }
        }

        return if (bestMse <= mseThreshold) bestAction else DouyinAction.NONE
    }

    private fun stabilize(action: DouyinAction): DouyinAction {
        return if (action == lastMatchedAction) {
            stableCount++
            if (stableCount >= holdFrames) {
                stableCount = 0
                trigger(action)
            } else {
                DouyinAction.NONE
            }
        } else {
            lastMatchedAction = action
            stableCount = 1
            DouyinAction.NONE
        }
    }

    private fun trigger(action: DouyinAction): DouyinAction {
        // 额外保护：不重复触发同一操作（除非冷却结束）
        if (action == lastTriggeredAction && cooldownRemaining > 0) {
            return DouyinAction.NONE
        }
        cooldownRemaining = cooldownFrames
        lastTriggeredAction = action
        lastMatchedAction = DouyinAction.NONE
        Log.d("GestureDetector", "触发: ${action.displayName}")
        return action
    }

    fun resetEmaBuffer() {
        emaBuffer.initialized = false
    }

    private fun resetState() {
        lastMatchedAction = DouyinAction.NONE
        stableCount = 0
    }
}
