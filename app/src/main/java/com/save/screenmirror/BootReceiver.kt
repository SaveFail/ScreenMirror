package com.save.screenmirror

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al encender el movil, arranca el servicio de presencia si esta activado. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.backgroundEnabled(context)
        ) {
            PresenceService.start(context)
        }
    }
}
