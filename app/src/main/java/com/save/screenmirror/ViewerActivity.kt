package com.save.screenmirror

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * Seccion "Ver pantalla": la app actua como RECEPTOR.
 * Carga la pagina del dispositivo emisor (http://IP:8080) en un WebView,
 * que muestra el stream MJPEG en vivo. Es la mitad "bidireccional" del sistema:
 * cualquier telefono con la app puede emitir o ver.
 */
class ViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: android.widget.EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Ver pantalla"

        val pad = (8 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        urlInput = android.widget.EditText(this).apply {
            hint = "http://IP-DEL-EMISOR:8080"
            setSingleLine(true)
        }

        val connectBtn = Button(this).apply {
            text = "Ver"
            setOnClickListener { loadUrl(urlInput.text.toString()) }
        }

        val scanBtn = Button(this).apply {
            text = "Escanear QR"
            setOnClickListener { openScanner() }
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(urlInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(connectBtn)
        root.addView(row)
        root.addView(scanBtn)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)

        // Si nos llega una URL por Intent (p.ej. desde el escaner), la cargamos.
        intent?.getStringExtra(EXTRA_URL)?.let {
            urlInput.setText(it)
            loadUrl(it)
        }
    }

    private fun openScanner() {
        startActivityForResult(Intent(this, QrScanActivity::class.java), REQ_SCAN)
    }

    @Deprecated("Usa el resultado del escaner")
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

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val REQ_SCAN = 300
    }
}
