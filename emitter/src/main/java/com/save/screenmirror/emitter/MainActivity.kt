package com.save.screenmirror.emitter

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.save.screenmirror.core.NetworkUtils

/**
 * App EMISORA: pide permiso de captura, arranca el servicio y muestra el QR + URL
 * para que la app Receptora (o un navegador) vea esta pantalla.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var urlView: TextView
    private lateinit var qrView: ImageView
    private var projectionManager: MediaProjectionManager? = null

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val intent = Intent(this, EmitterService::class.java).apply {
                    action = EmitterService.ACTION_START
                    putExtra(EmitterService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(EmitterService.EXTRA_DATA, data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                statusView.text = "Iniciando..."
                statusView.postDelayed({ updateUi(EmitterService.isRunning) }, 500)
            } else {
                statusView.text = "Permiso de captura denegado"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val pad = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            gravity = Gravity.CENTER
        }

        statusView = TextView(this).apply {
            text = "Detenido"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }

        urlView = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextIsSelectable(true)
            setPadding(0, pad, 0, pad)
        }

        qrView = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val size = (240 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = pad / 2
                bottomMargin = pad / 2
            }
        }

        val qrHint = TextView(this).apply {
            text = "La app Receptora escanea este QR (o un navegador abre la URL)"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        }

        val startBtn = Button(this).apply {
            text = "Iniciar transmision"
            setOnClickListener { requestCapture() }
        }

        val stopBtn = Button(this).apply {
            text = "Detener"
            setOnClickListener { stopMirror() }
        }

        content.addView(title)
        content.addView(statusView)
        content.addView(urlView)
        content.addView(qrView)
        content.addView(qrHint)
        content.addView(startBtn)
        content.addView(stopBtn)

        setContentView(ScrollView(this).apply { addView(content) })

        requestNotificationPermission()
        updateUi(EmitterService.isRunning)
    }

    private fun requestCapture() {
        val manager = projectionManager ?: return
        try {
            captureLauncher.launch(manager.createScreenCaptureIntent())
        } catch (_: Exception) {
            statusView.text = "No se pudo pedir el permiso de captura"
        }
    }

    private fun stopMirror() {
        val intent = Intent(this, EmitterService::class.java).apply {
            action = EmitterService.ACTION_STOP
        }
        try { startService(intent) } catch (_: Exception) {}
        stopService(Intent(this, EmitterService::class.java))
        updateUi(false)
    }

    private fun updateUi(running: Boolean) {
        statusView.text = if (running) "TRANSMITIENDO" else "Detenido"
        refreshUrl()
    }

    private fun refreshUrl() {
        val ip = NetworkUtils.getLocalIp()
        if (ip == null) {
            urlView.text = "Sin Wi-Fi. Conectate a una red."
            qrView.setImageBitmap(null)
            return
        }
        val url = "http://$ip:${EmitterService.PORT}"
        urlView.text = url
        val sizePx = (240 * resources.displayMetrics.density).toInt()
        qrView.setImageBitmap(generateQr(url, sizePx))
    }

    private fun generateQr(text: String, sizePx: Int): Bitmap? {
        return try {
            val hints = HashMap<EncodeHintType, Any>().apply {
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 10)
            } catch (_: Exception) {
            }
        }
    }
}
