package com.save.screenmirror

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla principal: pide permiso de captura, arranca/para el servicio
 * y muestra la URL que debe abrir el telefono receptor.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var urlView: TextView
    private var projectionManager: MediaProjectionManager? = null

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val intent = Intent(this, ScreenMirrorService::class.java).apply {
                    action = ScreenMirrorService.ACTION_START
                    putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenMirrorService.EXTRA_DATA, data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                statusView.postDelayed({ updateUi(ScreenMirrorService.isRunning) }, 400)
                statusView.text = "Iniciando..."
                urlView.text = urlText()
            } else {
                statusView.text = "Permiso de captura denegado"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
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

        val startBtn = Button(this).apply {
            text = "Iniciar transmision"
            setOnClickListener { requestCapture() }
        }

        val stopBtn = Button(this).apply {
            text = "Detener"
            setOnClickListener { stopMirror() }
        }

        root.addView(title)
        root.addView(statusView)
        root.addView(urlView)
        root.addView(startBtn)
        root.addView(stopBtn)
        setContentView(root)

        requestNotificationPermission()
        updateUi(ScreenMirrorService.isRunning)
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
        val intent = Intent(this, ScreenMirrorService::class.java).apply {
            action = ScreenMirrorService.ACTION_STOP
        }
        try { startService(intent) } catch (_: Exception) {}
        stopService(Intent(this, ScreenMirrorService::class.java))
        updateUi(false)
    }

    private fun updateUi(running: Boolean) {
        if (running) {
            statusView.text = "TRANSMITIENDO"
            urlView.text = urlText()
        } else {
            statusView.text = "Detenido"
            urlView.text = "Pulsa 'Iniciar' y acepta el permiso de captura."
        }
    }

    private fun urlText(): String {
        val ip = NetworkUtils.getLocalIp() ?: return "Sin Wi-Fi. Conectate a una red."
        return "Abre en el telefono receptor:\nhttp://$ip:${ScreenMirrorService.PORT}"
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
