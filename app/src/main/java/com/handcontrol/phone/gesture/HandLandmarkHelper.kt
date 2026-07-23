package com.handcontrol.phone.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

/**
 * 手部关键点归一化 + EMA 平滑 + 模板匹配
 *
 * 参照 hand-control-dy 项目的算法：
 * 1. EMA 指数平滑：减少手部抖动
 * 2. 归一化：以腕关节为原点，腕→中指MCP距离为缩放因子
 * 3. MSE 匹配：计算 21 点欧氏距离，取最小距离模板
 */
object HandLandmarkHelper {

    const val WRIST = 0
    const val MIDDLE_MCP = 9
    const val LANDMARK_COUNT = 21

    // ── EMA 平滑 ──
    // 平滑系数 α，值越小越平滑但响应越慢

    data class SmoothedLandmarks(
        var initialized: Boolean = false,
        val values: Array<FloatArray> = Array(LANDMARK_COUNT) { floatArrayOf(0f, 0f, 0f) }
    )

    /** 对一帧关键点做 EMA 平滑，返回平滑后的值 (in-place 更新 buffer) */
    fun smooth(
        landmarks: List<NormalizedLandmark>,
        buffer: SmoothedLandmarks,
        alpha: Float = 0.45f  // 对齐参考项目
    ) {
        if (!buffer.initialized) {
            for (i in 0 until LANDMARK_COUNT) {
                val lm = landmarks[i]
                buffer.values[i][0] = lm.x()
                buffer.values[i][1] = lm.y()
                buffer.values[i][2] = lm.z()
            }
            buffer.initialized = true
        } else {
            for (i in 0 until LANDMARK_COUNT) {
                val lm = landmarks[i]
                buffer.values[i][0] = alpha * lm.x() + (1 - alpha) * buffer.values[i][0]
                buffer.values[i][1] = alpha * lm.y() + (1 - alpha) * buffer.values[i][1]
                buffer.values[i][2] = alpha * lm.z() + (1 - alpha) * buffer.values[i][2]
            }
        }
    }

    // ── 归一化 ──

    /**
     * 归一化：以腕关节 (landmark[0]) 为原点，
     * 腕→中指 MCP (landmark[9]) 距离为缩放因子。
     * 输出 63 个浮点数 (21×3)
     */
    fun normalize(smoothed: Array<FloatArray>): FloatArray {
        val wrist = smoothed[WRIST]
        val middleMcp = smoothed[MIDDLE_MCP]

        // 缩放因子：手腕到中指MCP的距离，避免为 0
        val dx = middleMcp[0] - wrist[0]
        val dy = middleMcp[1] - wrist[1]
        val dz = middleMcp[2] - wrist[2]
        val scale = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)

        val result = FloatArray(LANDMARK_COUNT * 3)
        for (i in 0 until LANDMARK_COUNT) {
            result[i * 3]     = (smoothed[i][0] - wrist[0]) / scale
            result[i * 3 + 1] = (smoothed[i][1] - wrist[1]) / scale
            result[i * 3 + 2] = (smoothed[i][2] - wrist[2]) / scale
        }
        return result
    }

    // ── 编码/解码 (用于存储) ──

    fun encodeToString(template: FloatArray): String {
        return template.joinToString(",") { "%.6f".format(it) }
    }

    fun decodeFromString(str: String): FloatArray? {
        return try {
            val parts = str.split(",")
            if (parts.size != LANDMARK_COUNT * 3) return null
            FloatArray(parts.size) { parts[it].toFloat() }
        } catch (e: Exception) {
            null
        }
    }

    // ── MSE 匹配 ──

    /**
     * 计算两个归一化向量的均方误差 (MSE)
     * 返回值越小越相似，0 = 完全相同
     */
    fun mse(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sum / a.size
    }

    // ── 模板平均 (录制时用) ──

    /** 对多帧归一化向量取平均 */
    fun averageTemplates(templates: List<FloatArray>): FloatArray {
        val result = FloatArray(LANDMARK_COUNT * 3)
        for (template in templates) {
            for (i in result.indices) {
                result[i] += template[i]
            }
        }
        for (i in result.indices) {
            result[i] /= templates.size.toFloat()
        }
        return result
    }
}
