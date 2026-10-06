package com.save.screenmirror.viewer

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.save.screenmirror.core.DiscoveryClient

/**
 * App RECEPTORA: encuentra emisoras en la red (UDP), escanea su QR o acepta una URL
 * manual, y muestra la pantalla recibida en un WebView.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var statusView: TextView
    private lateinit var listView: ListView
    private lateinit var searchBtn: Button
    private lateinit var webView: WebView
    private val adapterItems = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (8 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        urlInput = EditText(this).apply { hint = "http://IP-DEL-EMISOR:8080"; setSingleLine(true) }
        val connectBtn = Button(this).apply {
            text = "Ver"
            setOnClickListener { loadUrl(urlInput.text.toString()) }
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(urlInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(connectBtn)

        searchBtn = Button(this).apply {
            text = "Buscar emisoras en la red"
            setOnClickListener { searchNetwork() }
        }
        val scanBtn = Button(this).apply {
            text = "Escanear QR"
            setOnClickListener { openScanner() }
        }
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(searchBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(scanBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        statusView = TextView(this).apply { text = "Listo" }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, adapterItems)
        listView = ListView(this).apply {
            this.adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                val url = adapterItems[position]
                urlInput.setText(url)
                loadUrl(url)
            }
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
        }

        root.addView(row1)
        root.addView(row2)
        root.addView(statusView)
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (120 * resources.displayMetrics.density).toInt()))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun searchNetwork() {
        searchBtn.isEnabled = false
        statusView.text = "Buscando emisoras en la red..."
        Thread {
            val devices = try {
                DiscoveryClient().discover()
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                adapterItems.clear()
                devices.forEach { adapterItems.add(it.url) }
                adapter.notifyDataSetChanged()
                statusView.text = if (devices.isEmpty()) {
                    "No se encontro ninguna emisora"
                } else {
                    "${devices.size} emisora(s). Toca una para ver."
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
        statusView.text = "Conectado a $url"
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
        private const val REQ_SCAN = 300
    }
}
