package com.handcontrol.phone.config

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.handcontrol.phone.R
import com.handcontrol.phone.gesture.DouyinAction
import com.handcontrol.phone.gesture.HandLandmarkHelper
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * 手势录制界面
 *
 * 用户对着摄像头做手势 → 采集 N 帧手指状态 → 取众数 → 保存
 */
class RecordGestureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACTION_CODE = "action_code"
        private const val TAG = "RecordGesture"
        private const val SAMPLE_FRAMES = 30  // 采集帧数
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
    private var collectedStates = mutableListOf<HandLandmarkHelper.FingerState>()
    private var frameCount = 0

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

        // 返回
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // 录制按钮
        btnRecord.setOnClickListener {
            if (isRecording) {
                scope.launch { stopRecording() }
            } else {
                startRecording()
            }
        }

        // 删除已录制
        findViewById<View>(R.id.btn_delete).setOnClickListener {
            scope.launch {
                mappingStore.deleteProfile(action)
                Toast.makeText(this@RecordGestureActivity, "已删除", Toast.LENGTH_SHORT).show()
                tvHint.text = "点击「开始录制」录入新手势"
            }
        }

        initHandLandmarker()
        startCamera()
    }

    // ── MediaPipe ──

    private fun initHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult?, _ ->
                    if (isRecording) {
                        processForRecording(result)
                    }
                }
                .setErrorListener { Log.e(TAG, "MediaPipe 错误: ${it.message}") }
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "HandLandmarker 初始化失败: ${e.message}", e)
            tvHint.text = "模型加载失败，请检查 hand_landmarker.task"
        }
    }

    private fun processForRecording(result: HandLandmarkerResult?) {
        val landmarks = result?.landmarks()?.firstOrNull() ?: return
        val state = HandLandmarkHelper.getFingerStates(landmarks)
        collectedStates.add(state)
        frameCount++

        scope.launch {
            tvProgress.text = "采集: $frameCount / $SAMPLE_FRAMES"
        }

        if (frameCount >= SAMPLE_FRAMES) {
            scope.launch { stopRecording() }
        }
    }

    // ── CameraX ──

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
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
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

        val handLandmarker = this.handLandmarker ?: return
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "检测异常: ${e.message}")
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21, android.graphics.ImageFormat.NV21,
            imageProxy.width, imageProxy.height, null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 80, out
        )
        var bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        val matrix = Matrix().apply {
            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // ── 录制控制 ──

    private fun startRecording() {
        isRecording = true
        collectedStates.clear()
        frameCount = 0
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

        if (collectedStates.isEmpty()) {
            tvHint.text = "未检测到手部，请重试"
            return
        }

        // 取众数（出现次数最多的手指状态编码）
        val codeCounts = collectedStates
            .map { GestureMappingStore.encodeFingerState(it) }
            .groupingBy { it }
            .eachCount()

        val bestCode = codeCounts.maxByOrNull { it.value }?.key ?: return
        val occurrence = codeCounts[bestCode] ?: 0
        val confidence = (occurrence * 100 / collectedStates.size)

        val bestState = GestureMappingStore.decodeFingerState(bestCode)

        // 保存
        mappingStore.saveProfile(action, bestState)

        val desc = GestureMappingStore.fingerStateDescription(bestState)
        tvHint.text = "✅ 录制成功！(置信度 $confidence%)\n$desc"
        Toast.makeText(this, "手势已保存: ${action.displayName}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        handLandmarker?.close()
        analysisExecutor.shutdown()
    }
}
