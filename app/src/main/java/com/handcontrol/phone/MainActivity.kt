package com.handcontrol.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.handcontrol.phone.action.GestureActionService
import com.handcontrol.phone.camera.OverlayService
import com.handcontrol.phone.config.ConfigActivity
import com.handcontrol.phone.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 摄像头权限请求
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                tryStartService()
            } else {
                Toast.makeText(this, "摄像头权限是必需的，请在设置中手动授权", Toast.LENGTH_LONG).show()
            }
            updateStatus()
        }

    // 悬浮窗权限请求
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateStatus() }

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
        binding.btnToggle.setOnClickListener {
            if (OverlayService.isRunning) {
                stopService()
            } else {
                tryStartService()
            }
        }
        binding.btnConfig.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
        binding.btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
        binding.btnOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }
        binding.btnCameraPermission.setOnClickListener {
            requestCameraPermission()
        }
    }

    private fun updateStatus() {
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

        val accessibilityRunning = GestureActionService.isRunning()
        val serviceRunning = OverlayService.isRunning

        binding.tvServiceStatus.text = when {
            serviceRunning -> getString(R.string.status_running)
            else -> getString(R.string.status_stopped)
        }
        binding.tvOverlayStatus.text = if (overlayGranted) "✓ 已授权" else "✗ 未授权"
        binding.tvCameraStatus.text = if (cameraGranted) "✓ 已授权" else "✗ 未授权"
        binding.tvAccessibilityStatus.text = if (accessibilityRunning) "✓ 已开启" else "✗ 未开启"

        binding.btnToggle.text = if (serviceRunning) {
            getString(R.string.stop_service)
        } else {
            getString(R.string.start_service)
        }

        binding.layoutPermissions.visibility =
            if (overlayGranted && accessibilityRunning && cameraGranted) View.GONE else View.VISIBLE
    }

    private fun tryStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        if (!GestureActionService.isRunning()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            openAccessibilitySettings()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }
        startService()
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
