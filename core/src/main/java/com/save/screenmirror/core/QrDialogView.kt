package com.save.screenmirror.core

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Construye la vista de un dialogo con un QR (para descargar la otra app).
 * Se crea por codigo para no depender de recursos entre modulos.
 */
object QrDialogView {

    fun build(context: Context, url: String, hint: String, qrSizePx: Int): View {
        val dp = context.resources.displayMetrics.density
        val pad = (24 * dp).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            val inner = (12 * dp).toInt()
            setPadding(inner, inner, inner, inner)
            gravity = Gravity.CENTER
        }
        val img = ImageView(context).apply {
            setImageBitmap(QrGenerator.generate(url, qrSizePx))
            layoutParams = LinearLayout.LayoutParams(qrSizePx, qrSizePx)
        }
        card.addView(img)
        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        val urlView = TextView(context).apply {
            text = url
            setTextIsSelectable(true)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }
        root.addView(
            urlView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val hintView = TextView(context).apply {
            text = hint
            textSize = 12f
            gravity = Gravity.CENTER
            alpha = 0.7f
            setPadding(0, (10 * dp).toInt(), 0, 0)
        }
        root.addView(
            hintView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return root
    }
}
