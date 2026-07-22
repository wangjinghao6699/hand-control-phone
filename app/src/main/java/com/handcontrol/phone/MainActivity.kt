package com.handcontrol.phone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.handcontrol.phone.action.GestureActionService
import com.handcontrol.phone.camera.OverlayService
import com.handcontrol.phone.config.ConfigActivity
import com.handcontrol.phone.databinding.ActivityMainBinding

/**
 * 主界面
 *
 * 提供：
 * - 状态显示（摄像头、无障碍服务状态）
 * - 启动/停止悬浮窗手势识别
 * - 进入手势配置界面
 * - 权限申请入口
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 悬浮窗权限请求
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupUI() {
        // 启动/停止按钮
        binding.btnToggle.setOnClickListener {
            if (OverlayService.isRunning) {
                stopService()
            } else {
                tryStartService()
            }
        }

        // 配置按钮
        binding.btnConfig.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }

        // 无障碍服务入口
        binding.btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // 悬浮窗权限入口
        binding.btnOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }
    }

    private fun updateStatus() {
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val accessibilityRunning = GestureActionService.isRunning()
        val serviceRunning = OverlayService.isRunning

        // 状态标签
        binding.tvServiceStatus.text = when {
            serviceRunning -> getString(R.string.status_running)
            else -> getString(R.string.status_stopped)
        }

        binding.tvOverlayStatus.text = if (overlayGranted) "✓ 已授权" else "✗ 未授权"
        binding.tvAccessibilityStatus.text = if (accessibilityRunning) "✓ 已开启" else "✗ 未开启"

        // 按钮文字
        binding.btnToggle.text = if (serviceRunning) {
            getString(R.string.stop_service)
        } else {
            getString(R.string.start_service)
        }

        // 权限提示可见性
        binding.layoutPermissions.visibility =
            if (overlayGranted && accessibilityRunning) View.GONE else View.VISIBLE
    }

    private fun tryStartService() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }

        // 检查无障碍服务
        if (!GestureActionService.isRunning()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            openAccessibilitySettings()
            return
        }

        startService()
    }

    private fun startService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus()
        Toast.makeText(this, "手势识别已启动", Toast.LENGTH_SHORT).show()

        // 自动返回桌面，让用户打开抖音
        moveTaskToBack(true)
    }

    private fun stopService() {
        stopService(Intent(this, OverlayService::class.java))
        updateStatus()
        Toast.makeText(this, "手势识别已停止", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "请在列表中找到「手势控制」并开启", Toast.LENGTH_LONG).show()
    }
}
