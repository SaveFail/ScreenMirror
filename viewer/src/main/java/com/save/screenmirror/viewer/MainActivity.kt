package com.save.screenmirror.viewer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.save.screenmirror.core.DiscoveryClient
import com.save.screenmirror.core.NetworkScanner
import com.save.screenmirror.core.QrDialogView

/**
 * App RECEPTORA: encuentra emisoras en la red (UDP + escaneo TCP), escanea su QR
 * o acepta una URL manual, y muestra la pantalla recibida en un WebView.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // URL directa a la ultima APK publicada de la app Emisora.
        private const val EMITTER_APK_URL =
            "https://github.com/SaveFail/ScreenMirror/releases/latest/download/emitter-release.apk"
        private const val REQ_SCAN = 300
    }

    private lateinit var urlInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var devicesCard: View
    private lateinit var devicesContainer: LinearLayout
    private lateinit var searchBtn: MaterialButton
    private lateinit var webView: WebView
    private lateinit var emptyState: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        statusText = findViewById(R.id.statusText)
        devicesCard = findViewById(R.id.devicesCard)
        devicesContainer = findViewById(R.id.devicesContainer)
        searchBtn = findViewById(R.id.searchBtn)
        webView = findViewById(R.id.webView)
        emptyState = findViewById(R.id.emptyState)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()

        findViewById<MaterialButton>(R.id.connectBtn).setOnClickListener {
            loadUrl(urlInput.text?.toString().orEmpty())
        }
        findViewById<MaterialButton>(R.id.scanBtn).setOnClickListener { openScanner() }
        searchBtn.setOnClickListener { searchNetwork() }
        findViewById<MaterialButton>(R.id.downloadBtn).setOnClickListener { showDownloadDialog() }
    }

    private fun searchNetwork() {
        searchBtn.isEnabled = false
        devicesCard.visibility = View.GONE
        statusText.text = getString(R.string.searching)
        Thread {
            val devices = try {
                // 1) Broadcast UDP (rapido si la red lo permite).
                val udp = try { DiscoveryClient().discover(1200) } catch (_: Exception) { emptyList() }
                // 2) Escaneo TCP del puerto de emision (fiable en cualquier red).
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
    }

    override fun onDestroy() {
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun showDownloadDialog() {
        val sizePx = (240 * resources.displayMetrics.density).toInt()
        val view = QrDialogView.build(this, EMITTER_APK_URL, getString(R.string.download_hint), sizePx)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_emitter_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
