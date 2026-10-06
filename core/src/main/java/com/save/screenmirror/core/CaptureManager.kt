package com.save.screenmirror.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import java.io.ByteArrayOutputStream

/** Captura la pantalla con MediaProjection y entrega cada cuadro como JPEG. */
class CaptureManager(
    private val context: Context,
    private val projection: MediaProjection,
    private val targetWidth: Int,
    private val onJpeg: (ByteArray) -> Unit
) {
    private val quality = 60
    private val minFrameIntervalMs = 100L

    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    @Volatile
    private var lastFrameAt = 0L

    fun start() {
        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val densityDpi = dm.densityDpi

        val w = if (screenW > targetWidth) targetWidth else screenW
        var h = (screenH.toFloat() * w / screenW).toInt()
        if (h % 2 == 1) h -= 1

        val ht = HandlerThread("screen-capture").also { it.start() }
        thread = ht
        handler = Handler(ht.looper)

        val ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = ir

        ir.setOnImageAvailableListener({ r ->
            val image: Image? = try {
                r.acquireLatestImage()
            } catch (_: Exception) {
                null
            }
            if (image == null) return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameAt >= minFrameIntervalMs) {
                    lastFrameAt = now
                    imageToJpeg(image, w, h)?.let(onJpeg)
                }
            } finally {
                try { image.close() } catch (_: Exception) {}
            }
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "ScreenMirror",
            w, h, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            ir.surface,
            null,
            handler
        )
    }

    private fun imageToJpeg(image: Image, w: Int, h: Int): ByteArray? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * w

            val bitmap = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, w, h)

            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)

            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            out.toByteArray()
        } catch (_: Throwable) {
            null
        }
    }

    fun stop() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { thread?.quitSafely() } catch (_: Exception) {}
        virtualDisplay = null
        reader = null
        thread = null
        handler = null
    }
}
