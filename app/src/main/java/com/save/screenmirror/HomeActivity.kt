package com.save.screenmirror

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.save.screenmirror.core.QrDialogView

/**
 * Pantalla principal de la app unificada: permite elegir entre EMITIR,
 * VER otra pantalla, o compartir la app mediante un QR.
 * Asi, dos personas con esta misma app pueden compartir en ambos sentidos.
 */
class HomeActivity : AppCompatActivity() {

    companion object {
        // URL directa a la ultima APK publicada de esta app.
        private const val APP_APK_URL =
            "https://github.com/SaveFail/ScreenMirror/releases/latest/download/app-release.apk"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<MaterialCardView>(R.id.emitCard).setOnClickListener {
            startActivity(Intent(this, EmitterActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.viewCard).setOnClickListener {
            startActivity(Intent(this, ViewerActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.shareCard).setOnClickListener {
            showShareDialog()
        }
    }

    private fun showShareDialog() {
        val sizePx = (240 * resources.displayMetrics.density).toInt()
        val view = QrDialogView.build(this, APP_APK_URL, getString(R.string.share_app_hint), sizePx)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.share_app_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
