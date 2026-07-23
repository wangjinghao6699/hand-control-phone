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
import kotlinx.coroutines.*

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
        loadTemplates()

        btnResetAll.setOnClickListener {
            scope.launch {
                mappingStore.resetAll()
                loadTemplates()
                Toast.makeText(this@ConfigActivity, "已清除全部模板", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTemplates() {
        scope.launch {
            val templates = mappingStore.getAllTemplates()
            adapter.submitList(templates)
        }
    }

    override fun onResume() {
        super.onResume()
        loadTemplates()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class ActionListAdapter(
    private val mappingStore: GestureMappingStore
) : RecyclerView.Adapter<ActionListAdapter.ViewHolder>() {

    private var templates: Map<DouyinAction, FloatArray> = emptyMap()

    fun submitList(newTemplates: Map<DouyinAction, FloatArray>) {
        templates = newTemplates
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = GestureMappingStore.RECORDABLE_ACTIONS[position]
        holder.bind(action, templates.containsKey(action))
    }

    override fun getItemCount(): Int = GestureMappingStore.RECORDABLE_ACTIONS.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvActionName: TextView = itemView.findViewById(R.id.tv_action_name)
        private val tvGestureDesc: TextView = itemView.findViewById(R.id.tv_gesture_desc)
        private val indicator: View = itemView.findViewById(R.id.indicator_recorded)

        fun bind(action: DouyinAction, hasTemplate: Boolean) {
            tvActionName.text = action.displayName
            if (hasTemplate) {
                tvGestureDesc.text = "✅ 已录制"
                indicator.setBackgroundResource(R.drawable.circle_green)
            } else {
                tvGestureDesc.text = "未录制 — 点击录制"
                indicator.setBackgroundResource(R.drawable.circle_accent)
            }
            itemView.setOnClickListener {
                val intent = Intent(itemView.context, RecordGestureActivity::class.java)
                intent.putExtra(RecordGestureActivity.EXTRA_ACTION_CODE, action.code)
                itemView.context.startActivity(intent)
            }
        }
    }
}
