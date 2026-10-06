package com.save.screenmirror.emitter

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.save.screenmirror.core.CaptureManager
import com.save.screenmirror.core.DiscoveryResponder
import com.save.screenmirror.core.NetworkUtils
import com.save.screenmirror.core.StreamServer

/**
 * Servicio en primer plano de la app EMISORA: captura la pantalla, la sirve por HTTP
 * (MJPEG) y responde al descubrimiento UDP para que la app Receptora la encuentre.
 */
class EmitterService : Service() {

    companion object {
        const val ACTION_START = "com.save.screenmirror.emitter.START"
        const val ACTION_STOP = "com.save.screenmirror.emitter.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        const val PORT = 8080
        private const val CHANNEL_ID = "emitter"
        private const val NOTIF_ID = 101

        @Volatile
        var isRunning = false
    }

    private var projection: MediaProjection? = null
    private var capture: CaptureManager? = null
    private var server: StreamServer? = null
    private var discovery: DiscoveryResponder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        if (data == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }

        startMirror(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startMirror(resultCode: Int, data: Intent) {
        createChannel()
        val ip = NetworkUtils.getLocalIp()
        val notification = buildNotification(
            if (ip != null) "Abre http://$ip:$PORT en el otro telefono" else "Conectate a una red Wi-Fi"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = try {
            mpm.getMediaProjection(resultCode, data)
        } catch (_: Exception) {
            null
        }
        if (proj == null) {
            stopSelf()
            return
        }
        projection = proj

        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        server = StreamServer(PORT).also { it.start() }
        discovery = DiscoveryResponder(PORT).also { it.start() }
        capture = CaptureManager(this, proj, targetWidth = 720) { jpeg ->
            server?.broadcast(jpeg)
        }.also { it.start() }

        isRunning = true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Transmision de pantalla",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("ScreenMirror Emisora activa")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        try { capture?.stop() } catch (_: Exception) {}
        try { server?.stop() } catch (_: Exception) {}
        try { discovery?.stop() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        capture = null
        server = null
        discovery = null
        projection = null
        super.onDestroy()
    }
}
