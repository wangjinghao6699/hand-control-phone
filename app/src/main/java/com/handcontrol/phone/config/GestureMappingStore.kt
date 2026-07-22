package com.handcontrol.phone.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.handcontrol.phone.gesture.DouyinAction
import com.handcontrol.phone.gesture.HandLandmarkHelper.FingerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gesture_profiles")

/**
 * 手势特征存储 — 操作 → 录制的手指状态
 *
 * 每个可录制操作对应一个 5-bit 手指状态编码：
 *   bit 0: 拇指伸直  bit 1: 食指伸直  bit 2: 中指伸直
 *   bit 3: 无名指伸直  bit 4: 小指伸直
 *
 * 例如：全部伸直 = 0b11111 = 31，全部卷曲 = 0b00000 = 0
 */
class GestureMappingStore(private val context: Context) {

    // ── 编码/解码 ──

    companion object {
        /** 可录制手势的操作列表（全部 7 个） */
        val RECORDABLE_ACTIONS = listOf(
            DouyinAction.SWIPE_UP,
            DouyinAction.SWIPE_DOWN,
            DouyinAction.LIKE,
            DouyinAction.FAVORITE,
            DouyinAction.PAUSE,
            DouyinAction.COMMENT,
            DouyinAction.PROFILE
        )

        fun encodeFingerState(state: FingerState): Int {
            var code = 0
            if (state.thumbExtended) code = code or 1
            if (state.indexExtended) code = code or 2
            if (state.middleExtended) code = code or 4
            if (state.ringExtended) code = code or 8
            if (state.pinkyExtended) code = code or 16
            return code
        }

        fun decodeFingerState(code: Int): FingerState {
            return FingerState(
                thumbExtended = (code and 1) != 0,
                indexExtended = (code and 2) != 0,
                middleExtended = (code and 4) != 0,
                ringExtended = (code and 8) != 0,
                pinkyExtended = (code and 16) != 0
            )
        }

        /** 计算两个手指状态的匹配度 (0-5) */
        fun matchScore(state1: FingerState, state2: FingerState): Int {
            var score = 0
            if (state1.thumbExtended == state2.thumbExtended) score++
            if (state1.indexExtended == state2.indexExtended) score++
            if (state1.middleExtended == state2.middleExtended) score++
            if (state1.ringExtended == state2.ringExtended) score++
            if (state1.pinkyExtended == state2.pinkyExtended) score++
            return score
        }

        fun fingerStateDescription(state: FingerState): String {
            val parts = mutableListOf<String>()
            if (state.thumbExtended) parts.add("拇指伸") else parts.add("拇指曲")
            if (state.indexExtended) parts.add("食指伸") else parts.add("食指曲")
            if (state.middleExtended) parts.add("中指伸") else parts.add("中指曲")
            if (state.ringExtended) parts.add("无名指伸") else parts.add("无名指曲")
            if (state.pinkyExtended) parts.add("小指伸") else parts.add("小指曲")
            return parts.joinToString(" ")
        }
    }

    // ── 读写 ──

    private fun profileKey(action: DouyinAction): Preferences.Key<Int> =
        intPreferencesKey("profile_${action.code}")

    /** 获取某个操作已录制的手势特征编码，未录制返回 null */
    suspend fun getProfileCode(action: DouyinAction): Int? {
        val prefs = context.dataStore.data.first()
        return prefs[profileKey(action)]
    }

    /** 获取所有已录制特征 (操作 → 编码) */
    suspend fun getAllProfiles(): Map<DouyinAction, Int> {
        val prefs = context.dataStore.data.first()
        val result = mutableMapOf<DouyinAction, Int>()
        RECORDABLE_ACTIONS.forEach { action ->
            prefs[profileKey(action)]?.let { result[action] = it }
        }
        return result
    }

    /** 保存录制的手势特征 */
    suspend fun saveProfile(action: DouyinAction, fingerState: FingerState) {
        context.dataStore.edit { prefs ->
            prefs[profileKey(action)] = encodeFingerState(fingerState)
        }
    }

    /** 获取所有已录制特征流 (用于 UI) */
    fun getAllProfilesFlow(): Flow<Map<DouyinAction, Int>> {
        return context.dataStore.data.map { prefs ->
            val result = mutableMapOf<DouyinAction, Int>()
            RECORDABLE_ACTIONS.forEach { action ->
                prefs[profileKey(action)]?.let { result[action] = it }
            }
            result
        }
    }

    /** 删除某个操作的录制 */
    suspend fun deleteProfile(action: DouyinAction) {
        context.dataStore.edit { prefs ->
            prefs.remove(profileKey(action))
        }
    }

    /** 全部删除 */
    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }

    /** 是否有任何已录制的手势 */
    suspend fun hasAnyProfile(): Boolean {
        val prefs = context.dataStore.data.first()
        return RECORDABLE_ACTIONS.any { prefs[profileKey(it)] != null }
    }
}
