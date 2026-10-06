package com.save.screenmirror

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Escaner de QR con la camara (CameraX + ZXing).
 * Devuelve el texto leido (la URL de un emisor) en EXTRA_RESULT.
 */
class QrScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESULT = "qr_result"
        private const val REQ_CAMERA = 200
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var hint: TextView
    private lateinit var analysisExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    @Volatile
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analysisExecutor = Executors.newSingleThreadExecutor()

        val root = FrameLayout(this)

        surfaceView = SurfaceView(this)
        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        hint = TextView(this).apply {
            text = "Apunta la camara al QR que muestra la app emisora"
            setBackgroundColor(0x99000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                startCamera()
            }

            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(h: SurfaceHolder) {}
        })

        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            hint.text = "Permiso de camara denegado"
        }
    }

    private fun startCamera() {
        if (cameraProvider != null || finished || !hasCameraPermission()) return

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider { request ->
                    val surface = surfaceView.holder.surface
                    if (surface.isValid) {
                        request.provideSurface(
                            surface,
                            ContextCompat.getMainExecutor(this)
                        ) { }
                    } else {
                        request.willNotProvideSurface()
                    }
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image -> analyze(image) }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                hint.text = "No se pudo abrir la camara: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        if (finished) {
            image.close()
            return
        }
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val source = PlanarYUVLuminanceSource(
                bytes, image.width, image.height, 0, 0, image.width, image.height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(bitmap)
            val text = result?.text

            if (!text.isNullOrBlank() && !finished) {
                finished = true
                runOnUiThread {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, text))
                    finish()
                }
            }
        } catch (_: Exception) {
            // Aun no hay un QR legible en este cuadro.
        } finally {
            try { image.close() } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        analysisExecutor.shutdown()
        super.onDestroy()
    }
}
