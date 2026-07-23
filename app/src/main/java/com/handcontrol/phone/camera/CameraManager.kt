package com.handcontrol.phone.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    var onHandLandmarksDetected: ((HandLandmarkerResult?) -> Unit)? = null
    var onPreviewBitmap: ((Bitmap) -> Unit)? = null

    private var handLandmarker: HandLandmarker? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    fun initHandLandmarker(modelPath: String = "hand_landmarker.task") {
        try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelPath).build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult?, _ -> onHandLandmarksDetected?.invoke(result) }
                .setErrorListener { Log.e(TAG, "MediaPipe 错误: ${it.message}") }
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.4f)
                .setMinTrackingConfidence(0.4f)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d(TAG, "HandLandmarker 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "HandLandmarker 初始化失败: ${e.message}", e)
        }
    }

    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(192, 192))
                .build()
                .also { it.setAnalyzer(analysisExecutor) { proxy -> processImageProxy(proxy) } }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                Log.d(TAG, "摄像头启动成功")
            } catch (e: Exception) {
                Log.e(TAG, "摄像头绑定失败: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ── 帧处理：直接从 Image 创建 MPImage，零拷贝 ──

    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }

        try {
            // 直接用 android.media.Image 构建 MPImage，跳过 YUV→JPEG→Bitmap
            val mpImage = MediaImageBuilder(mediaImage).build()
            val frameTime = System.currentTimeMillis()
            handLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "检测异常: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        try {
            handLandmarker?.close()
            handLandmarker = null
            analysisExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "关闭异常: ${e.message}")
        }
    }
}
