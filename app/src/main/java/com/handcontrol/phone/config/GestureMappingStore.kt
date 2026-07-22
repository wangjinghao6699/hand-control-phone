package com.handcontrol.phone.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.handcontrol.phone.gesture.DouyinAction
import com.handcontrol.phone.gesture.GestureType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gesture_mapping")

/**
 * 手势→操作映射的持久化存储
 *
 * 使用 DataStore 保存用户自定义的手势映射关系。
 * key 格式: "gesture_{GestureType.name}" → value: DouyinAction.code
 */
class GestureMappingStore(private val context: Context) {

    // ── 读写映射 ──

    /** 获取某个手势对应的操作 */
    fun getActionFlow(gesture: GestureType): Flow<DouyinAction> {
        val key = gestureKey(gesture)
        return context.dataStore.data.map { prefs ->
            val code = prefs[key] ?: gesture.defaultAction.code
            DouyinAction.fromCode(code)
        }
    }

    /** 一次性读取所有映射 */
    suspend fun getAllMappings(): Map<GestureType, DouyinAction> {
        val prefs = context.dataStore.data.first()
        val result = mutableMapOf<GestureType, DouyinAction>()
        GestureType.entries.filter { it != GestureType.UNKNOWN }.forEach { gesture ->
            val code = prefs[gestureKey(gesture)] ?: gesture.defaultAction.code
            result[gesture] = DouyinAction.fromCode(code)
        }
        return result
    }

    /** 设置映射 */
    suspend fun setMapping(gesture: GestureType, action: DouyinAction) {
        context.dataStore.edit { prefs ->
            prefs[gestureKey(gesture)] = action.code
        }
    }

    /** 重置单个手势为默认 */
    suspend fun resetToDefault(gesture: GestureType) {
        context.dataStore.edit { prefs ->
            prefs.remove(gestureKey(gesture))
        }
    }

    /** 重置全部手势为默认 */
    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }

    // ── 快速查找 ──

    /**
     * 根据识别到的手势，查找对应的操作。
     * 优先读取用户自定义映射，没有则使用默认映射。
     */
    suspend fun getAction(gesture: GestureType): DouyinAction {
        if (gesture == GestureType.UNKNOWN) return DouyinAction.NONE
        val prefs = context.dataStore.data.first()
        val code = prefs[gestureKey(gesture)]
        return if (code != null) DouyinAction.fromCode(code) else gesture.defaultAction
    }

    // ── 工具方法 ──

    private fun gestureKey(gesture: GestureType): Preferences.Key<Int> =
        intPreferencesKey("gesture_${gesture.name}")

    companion object {
        /** 获取手势的友好名称 */
        fun gestureDisplayName(gesture: GestureType): String = gesture.displayName

        /** 获取操作的友好名称 */
        fun actionDisplayName(action: DouyinAction): String = action.displayName

        /** 所有可映射的手势（排除 UNKNOWN） */
        val mappableGestures: List<GestureType> =
            GestureType.entries.filter { it != GestureType.UNKNOWN }

        /** 所有可映射的操作（排除 NONE） */
        val mappableActions: List<DouyinAction> = DouyinAction.ACTIONABLE
    }
}
