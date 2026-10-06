package com.save.screenmirror

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.save.screenmirror.core.QrDialogView
import com.save.screenmirror.core.Updater

/**
 * Pantalla principal de la app unificada: elegir entre EMITIR, VER otra pantalla,
 * compartir la app (eligiendo version), y buscar actualizaciones en el repositorio.
 * Asi, dos personas con esta misma app pueden compartir en ambos sentidos.
 */
class HomeActivity : AppCompatActivity() {

    companion object {
        private const val APK_NAME = "app-release.apk"
        private const val BASE_URL = "https://github.com/SaveFail/ScreenMirror/releases"
    }

    private lateinit var updateStatus: TextView
    private lateinit var updateBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        updateStatus = findViewById(R.id.updateStatus)
        updateBtn = findViewById(R.id.updateBtn)

        findViewById<MaterialCardView>(R.id.emitCard).setOnClickListener {
            startActivity(Intent(this, EmitterActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.viewCard).setOnClickListener {
            startActivity(Intent(this, ViewerActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.shareCard).setOnClickListener {
            showShareChooser()
        }
        updateBtn.setOnClickListener { checkForUpdate(auto = false) }

        val current = Updater.currentVersion(this) ?: "?"
        updateStatus.text = getString(R.string.installed_version, current)

        // Comprobacion automatica al abrir.
        checkForUpdate(auto = true)
    }

    /** Consulta la ultima release en GitHub y, si es mas nueva, ofrece instalarla. */
    private fun checkForUpdate(auto: Boolean) {
        updateBtn.isEnabled = false
        updateStatus.text = getString(R.string.update_checking)
        Thread {
            val current = Updater.currentVersion(this) ?: "?"
            val info = Updater.check(APK_NAME)
            runOnUiThread {
                updateBtn.isEnabled = true
                if (info == null) {
                    updateStatus.text = getString(R.string.installed_version, current)
                    if (!auto) Toast.makeText(this, R.string.update_error, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (Updater.isNewer(info.version, current)) {
                    updateStatus.text = getString(R.string.update_available_title) + ": " + info.tag
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.update_available_title)
                        .setMessage(getString(R.string.update_available_msg, info.tag, current))
                        .setPositiveButton(R.string.update_now) { _, _ -> downloadAndInstall(info.apkUrl) }
                        .setNegativeButton(R.string.update_later, null)
                        .show()
                } else {
                    updateStatus.text = getString(R.string.update_up_to_date, current)
                    if (!auto) Toast.makeText(this, getString(R.string.update_up_to_date, current), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun downloadAndInstall(url: String) {
        updateStatus.text = getString(R.string.update_downloading)
        Thread {
            val file = Updater.download(this, url, "update.apk")
            runOnUiThread {
                if (file == null) {
                    updateStatus.text = getString(R.string.update_download_error)
                    Toast.makeText(this, R.string.update_download_error, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                updateStatus.text = getString(R.string.update_installing)
                if (!Updater.canInstall(this)) {
                    Toast.makeText(this, R.string.update_need_permission, Toast.LENGTH_LONG).show()
                    Updater.openInstallSettings(this)
                    return@runOnUiThread
                }
                Updater.install(this, file)
            }
        }.start()
    }

    /** Deja elegir entre la version instalada y la ultima publicada, y muestra su QR. */
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
}
