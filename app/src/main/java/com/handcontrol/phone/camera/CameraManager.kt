package com.handcontrol.phone.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

/**
 * CameraX + MediaPipe HandLandmarker 管理器
 *
 * 职责：
 * 1. 管理摄像头预览（前置摄像头）
 * 2. 逐帧分析 → 调用 MediaPipe 检测手部关键点
 * 3. 通过回调将关键点数据传递给上层
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    // 回调：每一帧的 MediaPipe 检测结果
    var onHandLandmarksDetected: ((HandLandmarkerResult?) -> Unit)? = null

    // 回调：预览帧（用于悬浮窗显示）
    var onPreviewBitmap: ((Bitmap) -> Unit)? = null

    private var handLandmarker: HandLandmarker? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    // ──────────────────────────────────────────
    // 初始化
    // ──────────────────────────────────────────

    /**
     * 初始化 MediaPipe HandLandmarker
     * @param modelPath 模型文件在 assets 中的路径
     */
    fun initHandLandmarker(modelPath: String = "hand_landmarker.task") {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelPath)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult?, image: MPImage? ->
                    onHandLandmarksDetected?.invoke(result)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe 错误: ${error.message}")
                }
                .setNumHands(1) // 只检测一只手，提高性能
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d(TAG, "HandLandmarker 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "HandLandmarker 初始化失败: ${e.message}", e)
        }
    }

    // ──────────────────────────────────────────
    // 启动摄像头
    // ──────────────────────────────────────────

    /**
     * 绑定摄像头预览到指定的 PreviewView
     */
    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. 预览
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // 2. 帧分析（传递给 MediaPipe）
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(224, 224)) // 低分辨率加速推理
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            // 3. 选择前置摄像头
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                Log.d(TAG, "摄像头启动成功")
            } catch (e: Exception) {
                Log.e(TAG, "摄像头绑定失败: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ──────────────────────────────────────────
    // 帧处理
    // ──────────────────────────────────────────

    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // 转换为 Bitmap（旋转以匹配前置摄像头镜像）
        val bitmap = imageProxyToBitmap(imageProxy)
        imageProxy.close()

        if (bitmap == null) return

        // 回调预览帧
        onPreviewBitmap?.invoke(bitmap)

        // 送入手部关键点检测
        val handLandmarker = this.handLandmarker ?: return
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val frameTime = System.currentTimeMillis()
            handLandmarker.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe 检测异常: ${e.message}")
        }
    }

    // ──────────────────────────────────────────
    // 图片转换
    // ──────────────────────────────────────────

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
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            80,
            out
        )
        val jpegData = out.toByteArray()

        var bitmap = android.graphics.BitmapFactory.decodeByteArray(
            jpegData, 0, jpegData.size
        )

        // 前置摄像头需要水平翻转
        val matrix = Matrix().apply {
            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        bitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        return bitmap
    }

    // ──────────────────────────────────────────
    // 生命周期
    // ──────────────────────────────────────────

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
