package com.handcontrol.phone.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.handcontrol.phone.gesture.DouyinAction
import com.handcontrol.phone.gesture.HandLandmarkHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gesture_templates_v2")

/**
 * 手势模板存储 — 操作 → 归一化 63 维浮点向量
 *
 * 存储格式：逗号分隔的 63 个浮点数 (21 关键点 × XYZ)
 */
class GestureMappingStore(private val context: Context) {

    companion object {
        val RECORDABLE_ACTIONS = listOf(
            DouyinAction.SWIPE_UP,
            DouyinAction.SWIPE_DOWN,
            DouyinAction.LIKE,
            DouyinAction.FAVORITE,
            DouyinAction.PAUSE,
            DouyinAction.COMMENT,
            DouyinAction.PROFILE
        )
    }

    private fun profileKey(action: DouyinAction): Preferences.Key<String> =
        stringPreferencesKey("tpl_${action.code}")

    /** 获取某个操作的模板，未录制返回 null */
    suspend fun getTemplate(action: DouyinAction): FloatArray? {
        val prefs = context.dataStore.data.first()
        val str = prefs[profileKey(action)] ?: return null
        return HandLandmarkHelper.decodeFromString(str)
    }

    /** 获取所有已录制模板 */
    suspend fun getAllTemplates(): Map<DouyinAction, FloatArray> {
        val prefs = context.dataStore.data.first()
        val result = mutableMapOf<DouyinAction, FloatArray>()
        RECORDABLE_ACTIONS.forEach { action ->
            prefs[profileKey(action)]?.let { str ->
                HandLandmarkHelper.decodeFromString(str)?.let { result[action] = it }
            }
        }
        return result
    }

    /** 获取所有模板 Flow */
    fun getAllTemplatesFlow(): Flow<Map<DouyinAction, FloatArray>> {
        return context.dataStore.data.map { prefs ->
            val result = mutableMapOf<DouyinAction, FloatArray>()
            RECORDABLE_ACTIONS.forEach { action ->
                prefs[profileKey(action)]?.let { str ->
                    HandLandmarkHelper.decodeFromString(str)?.let { result[action] = it }
                }
            }
            result
        }
    }

    /** 保存模板 */
    suspend fun saveTemplate(action: DouyinAction, template: FloatArray) {
        context.dataStore.edit { prefs ->
            prefs[profileKey(action)] = HandLandmarkHelper.encodeToString(template)
        }
    }

    /** 删除模板 */
    suspend fun deleteTemplate(action: DouyinAction) {
        context.dataStore.edit { prefs -> prefs.remove(profileKey(action)) }
    }

    /** 全部删除 */
    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }

    /** 是否有任何模板 */
    suspend fun hasAny(): Boolean {
        val prefs = context.dataStore.data.first()
        return RECORDABLE_ACTIONS.any { prefs[profileKey(it)] != null }
    }
}
