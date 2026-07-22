package com.handcontrol.phone.config

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.handcontrol.phone.R
import com.handcontrol.phone.gesture.DouyinAction
import com.handcontrol.phone.gesture.HandLandmarkHelper
import kotlinx.coroutines.*

/**
 * 手势配置界面
 *
 * 显示固定的操作列表，每个操作显示录制状态，点击进入录制界面。
 */
class ConfigActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnResetAll: Button

    private val mappingStore by lazy { GestureMappingStore(this) }
    private val adapter by lazy { ActionListAdapter(mappingStore) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        recyclerView = findViewById(R.id.recycler_mappings)
        btnResetAll = findViewById(R.id.btn_reset_all)
        findViewById<View>(R.id.btn_back)?.setOnClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadProfiles()

        btnResetAll.setOnClickListener {
            scope.launch {
                mappingStore.resetAll()
                loadProfiles()
                Toast.makeText(this@ConfigActivity, "已清除所有录制", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProfiles() {
        scope.launch {
            val profiles = mappingStore.getAllProfiles()
            adapter.submitList(profiles)
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfiles()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

// ──────────────────────────────────────────────
// RecyclerView Adapter
// ──────────────────────────────────────────────

class ActionListAdapter(
    private val mappingStore: GestureMappingStore
) : RecyclerView.Adapter<ActionListAdapter.ViewHolder>() {

    // 已录制特征: 操作 → 编码
    private var profiles: Map<DouyinAction, Int> = emptyMap()

    fun submitList(newProfiles: Map<DouyinAction, Int>) {
        profiles = newProfiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = GestureMappingStore.RECORDABLE_ACTIONS[position]
        val code = profiles[action]
        holder.bind(action, code)
    }

    override fun getItemCount(): Int = GestureMappingStore.RECORDABLE_ACTIONS.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvActionName: TextView = itemView.findViewById(R.id.tv_action_name)
        private val tvGestureDesc: TextView = itemView.findViewById(R.id.tv_gesture_desc)
        private val indicator: View = itemView.findViewById(R.id.indicator_recorded)

        fun bind(action: DouyinAction, code: Int?) {
            tvActionName.text = action.displayName

            if (code != null) {
                val state = GestureMappingStore.decodeFingerState(code)
                tvGestureDesc.text = "✅ ${GestureMappingStore.fingerStateDescription(state)}"
                indicator.setBackgroundResource(R.drawable.circle_green)
            } else {
                tvGestureDesc.text = "未录制 — 点击右侧按钮录制"
                indicator.setBackgroundResource(R.drawable.circle_accent)
            }

            itemView.setOnClickListener {
                openRecordActivity(action)
            }
        }

        private fun openRecordActivity(action: DouyinAction) {
            val intent = Intent(itemView.context, RecordGestureActivity::class.java)
            intent.putExtra(RecordGestureActivity.EXTRA_ACTION_CODE, action.code)
            itemView.context.startActivity(intent)
        }
    }
}
