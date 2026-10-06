package com.save.screenmirror.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Comprueba la ultima release en GitHub y permite descargar e instalar la APK.
 * La instalacion siempre requiere la confirmacion del usuario (Android no permite
 * instalar en silencio).
 */
object Updater {

    const val REPO = "SaveFail/ScreenMirror"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    data class UpdateInfo(
        val tag: String,
        val version: String,
        val apkUrl: String,
        val releaseName: String
    )

    fun currentVersion(context: Context): String? {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        } catch (_: Exception) {
            null
        }
    }

    /** Consulta la ultima release y devuelve la info de la APK indicada. */
    fun check(assetName: String): UpdateInfo? {
        return try {
            val conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ScreenMirror")
                connectTimeout = 10000
                readTimeout = 10000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val tag = json.optString("tag_name")
            val version = tag.removePrefix("v")
            val name = json.optString("name")

            var url: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name") == assetName) {
                        url = a.optString("browser_download_url")
                        break
                    }
                }
            }
            if (url.isNullOrEmpty()) return null
            UpdateInfo(tag, version, url, name)
        } catch (_: Exception) {
            null
        }
    }

    /** true si [latest] es una version mas nueva que [current]. */
    fun isNewer(latest: String?, current: String?): Boolean {
        if (latest.isNullOrBlank() || current.isNullOrBlank()) return false
        val a = latest.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Descarga la APK a la cache. Devuelve el archivo o null. */
    fun download(context: Context, url: String, fileName: String): File? {
        return try {
            val dir = File(context.cacheDir, "apks").apply { mkdirs() }
            val out = File(dir, fileName)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "ScreenMirror")
            }
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            if (out.length() > 0) out else null
        } catch (_: Exception) {
            null
        }
    }

    /** true si la app tiene permiso para lanzar el instalador de paquetes. */
    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Abre los ajustes para permitir instalar apps desde esta app. */
    fun openInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /** Lanza el instalador del sistema para el archivo descargado. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
