package com.save.screenmirror

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.save.screenmirror.core.NetworkUtils
import com.save.screenmirror.core.QrDialogView
import com.save.screenmirror.core.QrGenerator

/**
 * App EMISORA: pide permiso de captura, arranca el servicio y muestra el QR + URL.
 * Mientras transmite mantiene la pantalla encendida y ofrece el QR para instalar
 * la app Receptora en el otro telefono.
 */
class EmitterActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTO_START = "auto_start"

        // URL directa a la ultima APK publicada de la app.
        private const val APP_APK_URL =
            "https://github.com/SaveFail/ScreenMirror/releases/latest/download/app-release.apk"
    }

    private lateinit var statusDot: android.view.View
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var urlText: TextView
    private lateinit var qrView: ImageView
    private var projectionManager: MediaProjectionManager? = null
    private var currentUrl: String? = null

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
                statusTitle.setText(R.string.emitter_status_starting)
                statusSubtitle.setText(R.string.emitter_idle_hint)
                statusTitle.postDelayed({ updateUi(EmitterService.isRunning) }, 500)
            } else {
                Toast.makeText(this, R.string.emitter_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emitter)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        statusDot = findViewById(R.id.statusDot)
        statusTitle = findViewById(R.id.statusTitle)
        statusSubtitle = findViewById(R.id.statusSubtitle)
        urlText = findViewById(R.id.urlText)
        qrView = findViewById(R.id.qr)

        findViewById<MaterialButton>(R.id.startBtn).setOnClickListener { requestCapture() }
        findViewById<MaterialButton>(R.id.stopBtn).setOnClickListener { stopMirror() }
        findViewById<MaterialButton>(R.id.copyBtn).setOnClickListener { copyUrl() }
        findViewById<MaterialButton>(R.id.downloadBtn).setOnClickListener { showDownloadDialog() }

        requestNotificationPermission()
        updateUi(EmitterService.isRunning)

        // Si se abrio con "Iniciar transmision al abrir", pedir el permiso automaticamente.
        if (intent?.getBooleanExtra(EXTRA_AUTO_START, false) == true) {
            statusTitle.postDelayed({ requestCapture() }, 500)
        }
    }

    private fun requestCapture() {
        val manager = projectionManager ?: return
        try {
            captureLauncher.launch(manager.createScreenCaptureIntent())
        } catch (_: Exception) {
            Toast.makeText(this, R.string.emitter_request_error, Toast.LENGTH_SHORT).show()
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
        if (running) {
            statusDot.setBackgroundResource(R.drawable.dot_live)
            statusTitle.setText(R.string.emitter_status_live)
            statusSubtitle.setText(R.string.emitter_live_hint)
        } else {
            statusDot.setBackgroundResource(R.drawable.dot_idle)
            statusTitle.setText(R.string.emitter_status_idle)
            statusSubtitle.setText(R.string.emitter_idle_hint)
        }
        // Mantener la pantalla encendida mientras se transmite.
        if (running) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        refreshUrl()
    }

    private fun refreshUrl() {
        val ip = NetworkUtils.getLocalIp()
        if (ip == null) {
            currentUrl = null
            urlText.setText(R.string.emitter_no_wifi)
            qrView.setImageBitmap(null)
            return
        }
        val url = "http://$ip:${EmitterService.PORT}"
        currentUrl = url
        urlText.text = url
        val sizePx = (240 * resources.displayMetrics.density).toInt()
        qrView.setImageBitmap(QrGenerator.generate(url, sizePx))
    }

    private fun copyUrl() {
        val url = currentUrl ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", url))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun showDownloadDialog() {
        val sizePx = (240 * resources.displayMetrics.density).toInt()
        val view = QrDialogView.build(this, APP_APK_URL, getString(R.string.share_app_hint), sizePx)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_app_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
