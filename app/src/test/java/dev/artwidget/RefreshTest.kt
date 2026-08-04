package dev.artwidget

import android.app.AlarmManager
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RefreshTest {

    @Test
    fun newArtReplacesCurrentArt() {
        val context = RuntimeEnvironment.getApplication()
        val store = ArtStore(context)
        val before = store.currentOrCreate()
        val done = CountDownLatch(1)
        Wallpaper.newArtAsync(context) { done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertNotEquals(before, store.current)
    }

    @Test
    fun wallpaperFollowsArtOnlyWhenEnabled() {
        val context = RuntimeEnvironment.getApplication()
        val store = ArtStore(context)
        val shadow = Shadows.shadowOf(WallpaperManager.getInstance(context))

        Wallpaper.applyIfEnabled(context)
        assertNull(shadow.getBitmap(WallpaperManager.FLAG_SYSTEM))

        store.wallpaperEnabled = true
        Wallpaper.applyIfEnabled(context)
        assertNotNull(shadow.getBitmap(WallpaperManager.FLAG_SYSTEM))
    }

    @Test
    fun blurAppliesToHomeScreenOnly() {
        val context = RuntimeEnvironment.getApplication()
        val store = ArtStore(context)
        val shadow = Shadows.shadowOf(WallpaperManager.getInstance(context))
        store.wallpaperEnabled = true
        store.blurEnabled = true
        store.blurPct = 30

        Wallpaper.applyIfEnabled(context)
        val home = shadow.getBitmap(WallpaperManager.FLAG_SYSTEM)
        val lock = shadow.getBitmap(WallpaperManager.FLAG_LOCK)
        assertNotNull(home)
        assertNotNull(lock)
        assertNotSame(lock, home)
    }

    @Test
    fun blurBottomFrostsBelowTheLineOnly() {
        // High-frequency stripes: any real blur must average them into mid-tones.
        val art = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(art)
        val paint = android.graphics.Paint()
        for (x in 0 until 100 step 4) {
            paint.color = if ((x / 4) % 2 == 0) Color.BLACK else Color.WHITE
            canvas.drawRect(x.toFloat(), 0f, x + 4f, 200f, paint)
        }
        val topBefore = art.getPixel(50, 5)
        val out = Wallpaper.blurBottom(art, Color.RED, 15)   // blur starts at y = 170

        assertEquals(topBefore, out.getPixel(50, 5))          // above the line untouched
        val frosted = out.getPixel(50, 195)
        assertTrue(
            "bottom should be blurred, got ${Integer.toHexString(frosted)}",
            frosted != Color.BLACK && frosted != Color.WHITE
        )

        val untouched = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        assertSame(untouched, Wallpaper.blurBottom(untouched, Color.RED, 0))
    }

    @Test
    fun scheduleArmsAndDisarmsTheAlarm() {
        val context = RuntimeEnvironment.getApplication()
        val alarms = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
        val store = ArtStore(context)

        store.refreshIntervalMillis = AlarmManager.INTERVAL_DAY
        RefreshReceiver.schedule(context)
        assertEquals(1, alarms.scheduledAlarms.size)

        store.refreshIntervalMillis = 0
        RefreshReceiver.schedule(context)
        assertEquals(0, alarms.scheduledAlarms.size)
    }
}
