package com.handcontrol.phone.config

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.io.ByteArrayOutputStream
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.handcontrol.phone.R
import com.handcontrol.phone.gesture.DouyinAction
import com.handcontrol.phone.gesture.HandLandmarkHelper
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * 手势录制界面 — 采集归一化特征模板
 *
 * 录制流程：用户保持手势 → 采集 N 帧归一化向量 → 取平均 → 保存为模板
 */
class RecordGestureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACTION_CODE = "action_code"
        private const val TAG = "RecordGesture"
        private const val SAMPLE_FRAMES = 25
    }

    private lateinit var previewView: PreviewView
    private lateinit var tvActionName: TextView
    private lateinit var tvHint: TextView
    private lateinit var btnRecord: View
    private lateinit var progressBar: View
    private lateinit var tvProgress: TextView

    private var handLandmarker: HandLandmarker? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var action: DouyinAction = DouyinAction.LIKE
    private var isRecording = false
    private var frameCount = 0
    private val collectedVectors = mutableListOf<FloatArray>()
    private val emaBuffer = HandLandmarkHelper.SmoothedLandmarks()

    private val mappingStore by lazy { GestureMappingStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_gesture)

        action = DouyinAction.fromCode(intent.getIntExtra(EXTRA_ACTION_CODE, -1))

        previewView = findViewById(R.id.preview_record)
        tvActionName = findViewById(R.id.tv_action_name)
        tvHint = findViewById(R.id.tv_record_hint)
        btnRecord = findViewById(R.id.btn_record)
        progressBar = findViewById(R.id.progress_bar)
        tvProgress = findViewById(R.id.tv_progress)

        tvActionName.text = "录制手势：${action.displayName}"

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        btnRecord.setOnClickListener {
            if (isRecording) {
                scope.launch { stopRecording() }
            } else {
                startRecording()
            }
        }

        findViewById<View>(R.id.btn_delete).setOnClickListener {
            scope.launch {
                mappingStore.deleteTemplate(action)
                Toast.makeText(this@RecordGestureActivity, "已删除", Toast.LENGTH_SHORT).show()
                tvHint.text = "点击「开始录制」录入新手势"
            }
        }

        initHandLandmarker()
        startCamera()
    }

    private fun initHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult?, _ ->
                    if (isRecording) processForRecording(result)
                }
                .setErrorListener { Log.e(TAG, "MediaPipe 错误: ${it.message}") }
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.6f)
                .setMinTrackingConfidence(0.5f)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "HandLandmarker 初始化失败: ${e.message}", e)
            tvHint.text = "模型加载失败"
        }
    }

    private fun processForRecording(result: HandLandmarkerResult?) {
        val landmarks = result?.landmarks()?.firstOrNull() ?: return
        if (landmarks.size < 21) return

        // EMA 平滑
        HandLandmarkHelper.smooth(landmarks, emaBuffer)
        if (!emaBuffer.initialized) return

        // 归一化并采集
        val normalized = HandLandmarkHelper.normalize(emaBuffer.values)
        collectedVectors.add(normalized)
        frameCount++

        scope.launch {
            tvProgress.text = "采集: $frameCount / $SAMPLE_FRAMES"
        }

        if (frameCount >= SAMPLE_FRAMES) {
            scope.launch { stopRecording() }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(224, 224))
                .build().also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "摄像头绑定失败: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)
        imageProxy.close()
        if (bitmap == null) return
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker?.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "检测异常: ${e.message}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        val ySize = yBuffer.remaining(); val uSize = uBuffer.remaining(); val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize); vBuffer.get(nv21, ySize, vSize); uBuffer.get(nv21, ySize + vSize, uSize)
        val yuv = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuv.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 50, out)
        var bmp = android.graphics.BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) ?: return null
        val m = android.graphics.Matrix().apply { postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f) }
        return android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun startRecording() {
        isRecording = true
        collectedVectors.clear()
        frameCount = 0
        emaBuffer.initialized = false
        btnRecord.isEnabled = false
        tvHint.text = "请保持手势不动…"
        tvProgress.text = "采集: 0 / $SAMPLE_FRAMES"
        tvProgress.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
    }

    private suspend fun stopRecording() {
        isRecording = false
        btnRecord.isEnabled = true
        progressBar.visibility = View.GONE
        tvProgress.visibility = View.GONE

        if (collectedVectors.size < 5) {
            tvHint.text = "采集不足（${collectedVectors.size} 帧），请重试"
            return
        }

        // 取平均作为模板
        val template = HandLandmarkHelper.averageTemplates(collectedVectors)

        // 自检：计算每帧与平均模板的 MSE，评估一致性
        var maxErr = 0f
        for (v in collectedVectors) {
            val err = HandLandmarkHelper.mse(v, template)
            if (err > maxErr) maxErr = err
        }

        // 保存
        mappingStore.saveTemplate(action, template)

        tvHint.text = if (maxErr < 0.02f) {
            "✅ 录制成功！(一致性良好, max MSE=${"%.4f".format(maxErr)})"
        } else {
            "⚠️ 已保存，但手势波动较大 (max MSE=${"%.4f".format(maxErr)})，建议重新录制"
        }
        Toast.makeText(this, "模板已保存: ${action.displayName}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        handLandmarker?.close()
        analysisExecutor.shutdown()
    }
}
