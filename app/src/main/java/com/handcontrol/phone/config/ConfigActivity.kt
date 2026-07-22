package com.handcontrol.phone.config

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
import com.handcontrol.phone.gesture.GestureType
import kotlinx.coroutines.*

/**
 * 手势配置界面
 *
 * 列表展示所有手势及其当前映射的操作，点击可修改映射。
 */
class ConfigActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnResetAll: Button

    private val mappingStore by lazy { GestureMappingStore(this) }
    private val adapter by lazy { GestureMappingAdapter(mappingStore) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        recyclerView = findViewById(R.id.recycler_mappings)
        btnResetAll = findViewById(R.id.btn_reset_all)

        // 返回按钮
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadMappings()

        btnResetAll.setOnClickListener {
            scope.launch {
                mappingStore.resetAll()
                loadMappings()
                Toast.makeText(this@ConfigActivity, "已恢复默认配置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMappings() {
        scope.launch {
            val mappings = mappingStore.getAllMappings()
            adapter.submitList(mappings.toList().sortedBy { it.first.ordinal })
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

// ──────────────────────────────────────────────
// RecyclerView Adapter
// ──────────────────────────────────────────────

class GestureMappingAdapter(
    private val mappingStore: GestureMappingStore
) : RecyclerView.Adapter<GestureMappingAdapter.ViewHolder>() {

    private var items: List<Pair<GestureType, DouyinAction>> = emptyList()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun submitList(newItems: List<Pair<GestureType, DouyinAction>>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gesture_mapping, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (gesture, action) = items[position]
        holder.bind(gesture, action)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvGestureName: TextView = itemView.findViewById(R.id.tv_gesture_name)
        private val tvGestureDesc: TextView = itemView.findViewById(R.id.tv_gesture_desc)
        private val spinnerAction: Spinner = itemView.findViewById(R.id.spinner_action)
        private val btnReset: ImageButton = itemView.findViewById(R.id.btn_reset_single)

        fun bind(gesture: GestureType, currentAction: DouyinAction) {
            tvGestureName.text = gesture.displayName
            tvGestureDesc.text = gesture.description

            // 操作选项适配器
            val actions = DouyinAction.ACTIONABLE
            val actionNames = actions.map { it.displayName }.toTypedArray()

            val adapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_dropdown_item,
                actionNames
            )
            spinnerAction.adapter = adapter

            // 设置当前选中项
            val currentIndex = actions.indexOfFirst { it == currentAction }
            if (currentIndex >= 0) {
                spinnerAction.setSelection(currentIndex)
            }

            // 监听选择变化
            var isInitialSelection = true
            spinnerAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (isInitialSelection) {
                        isInitialSelection = false
                        return
                    }
                    val newAction = actions[position]
                    scope.launch {
                        mappingStore.setMapping(gesture, newAction)
                        Toast.makeText(
                            itemView.context,
                            "已设置: ${gesture.displayName} → ${newAction.displayName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // 单条重置按钮
            btnReset.setOnClickListener {
                scope.launch {
                    mappingStore.resetToDefault(gesture)
                    val defaultAction = gesture.defaultAction
                    val defaultIndex = actions.indexOfFirst { it == defaultAction }
                    if (defaultIndex >= 0) {
                        spinnerAction.setSelection(defaultIndex)
                    }
                    Toast.makeText(
                        itemView.context,
                        "已恢复: ${gesture.displayName} → ${defaultAction.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
