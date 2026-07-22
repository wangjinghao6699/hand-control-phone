package com.handcontrol.phone.gesture

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

/**
 * 手势检测器
 *
 * - 静态手势：基于手指伸展状态直接判断
 * - 动态手势（滑动）：追踪手腕 Y 坐标的变化趋势
 * - 内置防抖：手势需持续 holdFrames 帧才触发
 * - 冷却机制：触发后冷却 cooldownFrames 帧避免重复
 */
class GestureDetector(
    private val holdFrames: Int = 8,         // 手势需保持的帧数
    private val cooldownFrames: Int = 25,     // 触发后冷却帧数
    private val swipeThreshold: Float = 0.06f // 滑动检测阈值
) {
    private var lastStableGesture: GestureType = GestureType.UNKNOWN
    private var stableFrameCount: Int = 0
    private var cooldownRemaining: Int = 0

    // 滑动检测状态
    private var wristYHistory = ArrayDeque<Float>(5)
    private var lastSwipeDirection: GestureType? = null
    private var swipeCheckFrames: Int = 0

    /**
     * 处理一帧关键点数据，返回当前识别到的手势（可能为 UNKNOWN）
     */
    fun processFrame(landmarks: List<NormalizedLandmark>?): GestureType {
        // 冷却计时
        if (cooldownRemaining > 0) {
            cooldownRemaining--
            return GestureType.UNKNOWN
        }

        if (landmarks.isNullOrEmpty() || landmarks.size < 21) {
            resetState()
            return GestureType.UNKNOWN
        }

        // 1. 先检测动态手势（滑动）
        val dynamicGesture = detectDynamicGesture(landmarks)
        if (dynamicGesture != GestureType.UNKNOWN) {
            return triggerGesture(dynamicGesture)
        }

        // 2. 检测静态手势
        val fingerState = HandLandmarkHelper.getFingerStates(landmarks)
        val staticGesture = HandLandmarkHelper.matchStaticGesture(fingerState)

        return if (staticGesture == GestureType.UNKNOWN) {
            resetState()
            GestureType.UNKNOWN
        } else {
            stabilizeGesture(staticGesture)
        }
    }

    // ──────────────────────────────────────────
    // 静态手势防抖
    // ──────────────────────────────────────────

    private fun stabilizeGesture(gesture: GestureType): GestureType {
        return if (gesture == lastStableGesture) {
            stableFrameCount++
            if (stableFrameCount >= holdFrames) {
                // 手势稳定足够帧数，触发
                stableFrameCount = 0
                gesture
            } else {
                GestureType.UNKNOWN
            }
        } else {
            lastStableGesture = gesture
            stableFrameCount = 1
            GestureType.UNKNOWN
        }
    }

    // ──────────────────────────────────────────
    // 动态手势检测（滑动）
    // ──────────────────────────────────────────

    private fun detectDynamicGesture(landmarks: List<NormalizedLandmark>): GestureType {
        val wrist = landmarks[HandLandmarkHelper.WRIST]
        val wristY = wrist.y()

        wristYHistory.addLast(wristY)
        if (wristYHistory.size > 5) {
            wristYHistory.removeFirst()
        }

        // 需要积累足够数据
        if (wristYHistory.size < 5) return GestureType.UNKNOWN

        // 计算总体位移
        val deltaY = wristYHistory.last() - wristYHistory.first()

        return when {
            // 向上滑动（Y 减小 = 手向上移动）
            deltaY < -swipeThreshold -> GestureType.SWIPE_UP
            // 向下滑动（Y 增大 = 手向下移动）
            deltaY > swipeThreshold -> GestureType.SWIPE_DOWN
            else -> GestureType.UNKNOWN
        }
    }

    // ──────────────────────────────────────────
    // 触发 & 冷却
    // ──────────────────────────────────────────

    private fun triggerGesture(gesture: GestureType): GestureType {
        cooldownRemaining = cooldownFrames
        wristYHistory.clear()
        resetState()
        Log.d("GestureDetector", "触发手势: ${gesture.displayName}")
        return gesture
    }

    private fun resetState() {
        lastStableGesture = GestureType.UNKNOWN
        stableFrameCount = 0
    }

    /** 获取当前手势的调试描述 */
    fun getDebugInfo(landmarks: List<NormalizedLandmark>?): String {
        if (landmarks.isNullOrEmpty()) return "未检测到手部"
        val state = HandLandmarkHelper.getFingerStates(landmarks)
        return "拇指:${b(state.thumbExtended)} 食指:${b(state.indexExtended)} " +
                "中指:${b(state.middleExtended)} 无名指:${b(state.ringExtended)} " +
                "小指:${b(state.pinkyExtended)} | 冷却:$cooldownRemaining"
    }

    private fun b(v: Boolean) = if (v) "伸" else "曲"
}
