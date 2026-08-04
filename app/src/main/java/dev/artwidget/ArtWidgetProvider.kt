package dev.artwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.Generator

class ArtWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onEnabled(context: Context) = RefreshReceiver.schedule(context)

    override fun onDisabled(context: Context) {
        // Keep the alarm when the art also drives the wallpaper — that outlives widgets.
        if (!ArtStore(context).wallpaperEnabled) RefreshReceiver.cancel(context)
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
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

        /** Re-renders every widget and, when enabled, the wallpaper, off the caller's thread. */
        fun pushAsync(context: Context) {
            val app = context.applicationContext
            executor.execute { push(app) }
        }

        /** Stores a freshly generated art and pushes it everywhere, off the caller's thread. */
        fun newArtAsync(context: Context, onDone: () -> Unit = {}) {
            val app = context.applicationContext
            executor.execute {
                try {
                    val store = ArtStore(app)
                    store.current = Generator.newArt(store.enabledPatterns(), app.isSystemDark())
                    push(app)
                } finally {
                    onDone()
                }
            }
        }

        private fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ArtWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
            Wallpaper.applyIfEnabled(context)
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val spec = ArtStore(context).currentOrCreate()
            val dm = context.resources.displayMetrics
            val density = dm.density
            val options = manager.getAppWidgetOptions(id)

            // The host reports both orientations' cell sizes at once; rotation fires no
            // callback, so we always supply a bitmap per orientation (or per reported
            // size on 31+) and let the launcher pick.
            val portW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val portH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val landW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val landH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            @Suppress("DEPRECATION")
            val sizes = if (Build.VERSION.SDK_INT >= 31) {
                options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            } else null
            val useSizes = !sizes.isNullOrEmpty() && sizes.size <= 3

            // Launchers cap a RemoteViews' total bitmap memory relative to the screen;
            // split a safe pixel budget across however many bitmaps we attach.
            val bitmapCount = if (useSizes) sizes!!.size else 2
            val maxPixels = (dm.widthPixels.toLong() * dm.heightPixels / bitmapCount)
                .coerceAtLeast(250_000L)

            val radius = if (Build.VERSION.SDK_INT >= 31) {
                try {
                    context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
                } catch (e: Exception) {
                    16f * density
                }
            } else 16f * density

            val tap = RefreshReceiver.newArtIntent(context)

            fun viewsFor(wDp: Int, hDp: Int): RemoteViews {
                var w = ((if (wDp > 0) wDp else 300) * density).toInt().coerceAtLeast(50)
                var h = ((if (hDp > 0) hDp else 300) * density).toInt().coerceAtLeast(50)
                var scaledRadius = radius
                val px = w.toLong() * h
                if (px > maxPixels) {
                    val s = kotlin.math.sqrt(maxPixels.toDouble() / px).toFloat()
                    w = (w * s).toInt().coerceAtLeast(50)
                    h = (h * s).toInt().coerceAtLeast(50)
                    scaledRadius = radius * s
                }
                val art = ArtRenderer.rounded(ArtRenderer.render(spec, w, h), scaledRadius)
                val views = RemoteViews(context.packageName, R.layout.widget_art)
                views.setImageViewBitmap(R.id.widget_image, art)
                views.setOnClickPendingIntent(R.id.widget_image, tap)
                return views
            }

            val views = if (useSizes && Build.VERSION.SDK_INT >= 31) {
                RemoteViews(sizes!!.associateWith { viewsFor(it.width.toInt(), it.height.toInt()) })
            } else {
                RemoteViews(viewsFor(landW, landH), viewsFor(portW, portH))
            }
            manager.updateAppWidget(id, views)
        }
    }
}
