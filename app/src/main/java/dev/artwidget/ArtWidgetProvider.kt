package dev.artwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import dev.artwidget.art.ArtRenderer

class ArtWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: android.os.Bundle
    ) {
        update(context, manager, id)
    }

    companion object {
        /** Re-renders every placed widget with the store's current art. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ArtWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val spec = ArtStore(context).currentOrCreate()
            val density = context.resources.displayMetrics.density
            val options = manager.getAppWidgetOptions(id)
            val portrait = context.resources.configuration.orientation !=
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val wDp = options.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            )
            val hDp = options.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            )
            val w = ((if (wDp > 0) wDp else 300) * density).toInt().coerceIn(50, 1200)
            val h = ((if (hDp > 0) hDp else 300) * density).toInt().coerceIn(50, 1200)

            val radius = if (Build.VERSION.SDK_INT >= 31) {
                try {
                    context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
                } catch (e: Exception) {
                    16f * density
                }
            } else 16f * density

            val art = ArtRenderer.rounded(ArtRenderer.render(spec, w, h), radius)

            val views = RemoteViews(context.packageName, R.layout.widget_art)
            views.setImageViewBitmap(R.id.widget_image, art)
            val tap = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, tap)
            manager.updateAppWidget(id, views)
        }
    }
}
