package com.handcontrol.phone.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 手部关键点辅助计算工具
 *
 * MediaPipe Hands 返回 21 个归一化关键点（0-1 相对于图像尺寸）：
 *   0: 手腕 WRIST
 *   1-4: 拇指 THUMB (CMC→MCP→IP→TIP)
 *   5-8: 食指 INDEX (MCP→PIP→DIP→TIP)
 *   9-12: 中指 MIDDLE (MCP→PIP→DIP→TIP)
 *   13-16: 无名指 RING (MCP→PIP→DIP→TIP)
 *   17-20: 小指 PINKY (MCP→PIP→DIP→TIP)
 */
object HandLandmarkHelper {

    // ── 关键点索引常量 ──
    const val WRIST = 0
    const val THUMB_TIP = 4
    const val THUMB_IP = 3
    const val INDEX_TIP = 8
    const val INDEX_PIP = 6
    const val INDEX_MCP = 5
    const val MIDDLE_TIP = 12
    const val MIDDLE_PIP = 10
    const val MIDDLE_MCP = 9
    const val RING_TIP = 16
    const val RING_PIP = 14
    const val PINKY_TIP = 20
    const val PINKY_PIP = 18

    // ── 辅助结构 ──
    data class Point3D(val x: Float, val y: Float, val z: Float)

    fun NormalizedLandmark.toPoint3D() = Point3D(x(), y(), z())

    fun distance(a: Point3D, b: Point3D): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // ── 手指伸展判断 ──

    /**
     * 判断拇指是否竖起（拇指尖高于拇指 IP 关节，并且远离食指 MCP）
     */
    fun isThumbUp(landmarks: List<NormalizedLandmark>): Boolean {
        val tip = landmarks[THUMB_TIP]
        val ip = landmarks[THUMB_IP]
        val indexMcp = landmarks[INDEX_MCP]
        // 拇指尖相对 IP 关节明显向上，且与食指 MCP 保持距离
        return (ip.y() - tip.y()) > 0.04f &&
                distance(tip.toPoint3D(), indexMcp.toPoint3D()) > 0.08f
    }

    /**
     * 判断手指是否伸直：指尖到 MCP 的距离 > PIP 到 MCP 的距离 + 阈值
     */
    fun isFingerExtended(
        landmarks: List<NormalizedLandmark>,
        tipIdx: Int, pipIdx: Int, mcpIdx: Int
    ): Boolean {
        val tip = landmarks[tipIdx].toPoint3D()
        val pip = landmarks[pipIdx].toPoint3D()
        val mcp = landmarks[mcpIdx].toPoint3D()
        val tipToMcp = distance(tip, mcp)
        val pipToMcp = distance(pip, mcp)
        return tipToMcp > pipToMcp + 0.05f
    }

    /** 食指伸直 */
    fun isIndexExtended(landmarks: List<NormalizedLandmark>): Boolean =
        isFingerExtended(landmarks, INDEX_TIP, INDEX_PIP, INDEX_MCP)

    /** 中指伸直 */
    fun isMiddleExtended(landmarks: List<NormalizedLandmark>): Boolean =
        isFingerExtended(landmarks, MIDDLE_TIP, MIDDLE_PIP, MIDDLE_MCP)

    /** 无名指伸直 */
    fun isRingExtended(landmarks: List<NormalizedLandmark>): Boolean =
        isFingerExtended(landmarks, RING_TIP, RING_PIP, 13)

    /** 小指伸直 */
    fun isPinkyExtended(landmarks: List<NormalizedLandmark>): Boolean =
        isFingerExtended(landmarks, PINKY_TIP, PINKY_PIP, 17)

    /** 所有手指的伸展状态 */
    data class FingerState(
        val thumbExtended: Boolean,
        val indexExtended: Boolean,
        val middleExtended: Boolean,
        val ringExtended: Boolean,
        val pinkyExtended: Boolean
    )

    fun getFingerStates(landmarks: List<NormalizedLandmark>): FingerState {
        return FingerState(
            thumbExtended = isThumbUp(landmarks),
            indexExtended = isIndexExtended(landmarks),
            middleExtended = isMiddleExtended(landmarks),
            ringExtended = isRingExtended(landmarks),
            pinkyExtended = isPinkyExtended(landmarks)
        )
    }

    // ── 手势匹配 ──

    /**
     * 根据手指伸展状态匹配静态手势
     */
    fun matchStaticGesture(state: FingerState): GestureType {
        return when {
            // 五指全伸 → 张开手掌
            state.thumbExtended && state.indexExtended &&
                    state.middleExtended && state.ringExtended && state.pinkyExtended ->
                GestureType.OPEN_PALM

            // 只有拇指竖起，其他卷曲 → 点赞手势
            state.thumbExtended && !state.indexExtended &&
                    !state.middleExtended && !state.ringExtended && !state.pinkyExtended ->
                GestureType.THUMB_UP

            // 只有食指伸出，其他卷曲 → 食指指向
            !state.thumbExtended && state.indexExtended &&
                    !state.middleExtended && !state.ringExtended && !state.pinkyExtended ->
                GestureType.INDEX_POINT

            // 食指和中指伸出，其他卷曲 → V字手势
            !state.thumbExtended && state.indexExtended &&
                    state.middleExtended && !state.ringExtended && !state.pinkyExtended ->
                GestureType.PEACE_SIGN

            // 五指全卷曲 → 握拳
            !state.thumbExtended && !state.indexExtended &&
                    !state.middleExtended && !state.ringExtended && !state.pinkyExtended ->
                GestureType.FIST

            else -> GestureType.UNKNOWN
        }
    }
}
