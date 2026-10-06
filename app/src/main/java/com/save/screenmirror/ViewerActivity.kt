package com.save.screenmirror

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.save.screenmirror.core.DiscoveryClient
import com.save.screenmirror.core.NetworkScanner
import com.save.screenmirror.core.QrDialogView
import com.save.screenmirror.core.Updater

/**
 * App RECEPTORA a PANTALLA COMPLETA: descubre emisoras en la red (UDP + escaneo TCP),
 * escanea su QR o acepta una URL manual, y muestra la pantalla recibida ocupando toda
 * la pantalla. Los controles se ocultan (modo inmersivo) y se muestran con un boton.
 */
class ViewerActivity : AppCompatActivity() {

    companion object {
        private const val APK_NAME = "app-release.apk"
        private const val BASE_URL = "https://github.com/SaveFail/ScreenMirror/releases"
        private const val REQ_SCAN = 300
    }

    private lateinit var urlInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var devicesCard: View
    private lateinit var devicesContainer: LinearLayout
    private lateinit var searchBtn: MaterialButton
    private lateinit var webView: WebView
    private lateinit var emptyState: View
    private lateinit var controlsPanel: View
    private lateinit var controlsFab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        urlInput = findViewById(R.id.urlInput)
        statusText = findViewById(R.id.statusText)
        devicesCard = findViewById(R.id.devicesCard)
        devicesContainer = findViewById(R.id.devicesContainer)
        searchBtn = findViewById(R.id.searchBtn)
        webView = findViewById(R.id.webView)
        emptyState = findViewById(R.id.emptyState)
        controlsPanel = findViewById(R.id.controlsPanel)
        controlsFab = findViewById(R.id.controlsFab)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Al cargar la pagina, pasamos a pantalla completa.
                setControlsVisible(false)
            }
        }

        findViewById<MaterialButton>(R.id.connectBtn).setOnClickListener {
            loadUrl(urlInput.text?.toString().orEmpty())
        }
        findViewById<MaterialButton>(R.id.scanBtn).setOnClickListener { openScanner() }
        searchBtn.setOnClickListener { searchNetwork() }
        findViewById<MaterialButton>(R.id.downloadBtn).setOnClickListener { showShareChooser() }
        controlsFab.setOnClickListener {
            setControlsVisible(controlsPanel.visibility != View.VISIBLE)
        }

        // Atras: si los controles estan ocultos, mostrarlos; si no, salir.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (controlsPanel.visibility != View.VISIBLE) {
                    setControlsVisible(true)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setControlsVisible(true)
    }

    /** Muestra u oculta los controles y activa el modo inmersivo (pantalla completa). */
    private fun setControlsVisible(visible: Boolean) {
        controlsPanel.visibility = if (visible) View.VISIBLE else View.GONE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun searchNetwork() {
        searchBtn.isEnabled = false
        devicesCard.visibility = View.GONE
        statusText.text = getString(R.string.searching)
        Thread {
            val devices = try {
                val udp = try { DiscoveryClient().discover(1200) } catch (_: Exception) { emptyList() }
                val tcp = try { NetworkScanner.scan() } catch (_: Exception) { emptyList() }
                val merged = LinkedHashMap<String, com.save.screenmirror.core.Discovery.Device>()
                udp.forEach { merged[it.host] = it }
                tcp.forEach { merged[it.host] = it }
                merged.values.toList()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                devicesContainer.removeAllViews()
                if (devices.isEmpty()) {
                    devicesCard.visibility = View.GONE
                    statusText.text = getString(R.string.no_devices)
                } else {
                    val inflater = LayoutInflater.from(this)
                    devices.forEach { device ->
                        val row = inflater.inflate(R.layout.item_device, devicesContainer, false)
                        row.findViewById<TextView>(R.id.deviceUrl).text = device.url
                        row.setOnClickListener {
                            urlInput.setText(device.url)
                            loadUrl(device.url)
                        }
                        devicesContainer.addView(row)
                    }
                    devicesCard.visibility = View.VISIBLE
                    statusText.text = getString(R.string.devices_found, devices.size)
                }
                searchBtn.isEnabled = true
            }
        }.start()
    }

    private fun openScanner() {
        startActivityForResult(Intent(this, QrScanActivity::class.java), REQ_SCAN)
    }

    @Deprecated("Resultado del escaner")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SCAN && resultCode == RESULT_OK) {
            val url = data?.getStringExtra(QrScanActivity.EXTRA_RESULT)
            if (!url.isNullOrBlank()) {
                urlInput.setText(url)
                loadUrl(url)
            }
        }
    }

    private fun loadUrl(raw: String) {
        var url = raw.trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        statusText.text = getString(R.string.connected_to, url)
        devicesCard.visibility = View.GONE
        emptyState.visibility = View.GONE
        webView.loadUrl(url)
        setControlsVisible(false)
    }

    private fun showShareChooser() {
        val current = Updater.currentVersion(this) ?: "?"
        val options = arrayOf(
            getString(R.string.share_option_installed, current),
            getString(R.string.share_option_latest)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_choose_title)
            .setItems(options) { _, which ->
                val url = if (which == 0) {
                    "$BASE_URL/download/v$current/$APK_NAME"
                } else {
                    "$BASE_URL/latest/download/$APK_NAME"
                }
                showQrDialog(url)
            }
            .show()
    }

    private fun showQrDialog(url: String) {
        val sizePx = (240 * resources.displayMetrics.density).toInt()
        val view = QrDialogView.build(this, url, getString(R.string.share_app_hint), sizePx)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_app_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroy() {
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
