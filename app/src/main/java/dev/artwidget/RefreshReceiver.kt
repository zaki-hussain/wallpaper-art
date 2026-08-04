package dev.artwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Non-exported receiver behind the auto-refresh alarm: swaps in a freshly generated
 * art and re-applies the wallpaper. Also re-arms the alarm after reboots and updates.
 */
class RefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEW_ART -> {
                val result = goAsync()
                Wallpaper.newArtAsync(context) { result.finish() }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> schedule(context)
        }
    }

    companion object {
        private const val ACTION_NEW_ART = "dev.artwidget.NEW_ART"

        fun newArtIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, RefreshReceiver::class.java).setAction(ACTION_NEW_ART),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /**
         * Arms the refresh alarm from the stored interval, or disarms it when off.
         * Inexact + non-waking, so refreshes ride along with normal device wake-ups.
         */
        fun schedule(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val interval = ArtStore(context).refreshIntervalMillis
            if (interval > 0) {
                alarms.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + interval,
                    interval,
                    newArtIntent(context)
                )
            } else {
                alarms.cancel(newArtIntent(context))
            }
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(newArtIntent(context))
        }
    }
}
