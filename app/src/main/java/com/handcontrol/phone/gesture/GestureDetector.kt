package com.handcontrol.phone.gesture

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * 手势检测器 — 归一化 MSE 匹配 + EMA 平滑
 *
 * 参数对齐 hand-control-dy 参考项目
 */
class GestureDetector(
    private val holdFrames: Int = 4,              // 确认帧数 (对齐参考项目)
    private val cooldownFrames: Int = 20,          // 冷却帧数 ~1.5s @13fps (对齐参考项目)
    private val mseThreshold: Float = 0.06f,       // MSE 阈值 (参考项目 0.25 Euclidean ≈ 0.0625 MSE)
    private val hysteresisMargin: Float = 0.15f    // 切换迟滞：新手势需比当前好 15% (对齐参考项目)
) {
    var recordedTemplates: Map<DouyinAction, FloatArray> = emptyMap()

    private val emaBuffer = HandLandmarkHelper.SmoothedLandmarks()
    private var currentBestAction: DouyinAction = DouyinAction.NONE
    private var currentBestScore: Float = Float.MAX_VALUE
    private var stableCount: Int = 0
    private var cooldownRemaining: Int = 0
    private var lastActionTime: Long = 0

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
        val (action, score) = findBestMatch(normalized)
        if (action == DouyinAction.NONE) {
            resetState()
            return DouyinAction.NONE
        }

        // 4. 迟滞：新手势必须明显更好才切换（分数低 15% 以上）
        if (action == currentBestAction) {
            stableCount++
            currentBestScore = score
        } else if (currentBestAction == DouyinAction.NONE || score < currentBestScore * (1 - hysteresisMargin)) {
            currentBestAction = action
            currentBestScore = score
            stableCount = 1
        }
        // 否则忽略（噪音），不增减计数

        // 5. 确认帧数达标 → 触发
        if (stableCount >= holdFrames) {
            return trigger(action)
        }
        return DouyinAction.NONE
    }

    private fun findBestMatch(normalized: FloatArray): Pair<DouyinAction, Float> {
        if (recordedTemplates.isEmpty()) return DouyinAction.NONE to Float.MAX_VALUE

        var bestAction = DouyinAction.NONE
        var bestMse = Float.MAX_VALUE

        for ((action, template) in recordedTemplates) {
            val err = HandLandmarkHelper.mse(normalized, template)
            if (err < bestMse) {
                bestMse = err
                bestAction = action
            }
        }

        return if (bestMse <= mseThreshold) bestAction to bestMse
        else DouyinAction.NONE to Float.MAX_VALUE
    }

    private fun trigger(action: DouyinAction): DouyinAction {
        cooldownRemaining = cooldownFrames
        resetState()
        Log.d("GestureDetector", "触发: ${action.displayName}")
        return action
    }

    fun resetEmaBuffer() {
        emaBuffer.initialized = false
    }

    private fun resetState() {
        currentBestAction = DouyinAction.NONE
        currentBestScore = Float.MAX_VALUE
        stableCount = 0
    }
}
