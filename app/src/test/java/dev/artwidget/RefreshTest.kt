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
    fun fadeAppliesToHomeScreenOnly() {
        val context = RuntimeEnvironment.getApplication()
        val store = ArtStore(context)
        val shadow = Shadows.shadowOf(WallpaperManager.getInstance(context))
        store.wallpaperEnabled = true
        store.fadeEnabled = true
        store.fadePct = 30

        Wallpaper.applyIfEnabled(context)
        val home = shadow.getBitmap(WallpaperManager.FLAG_SYSTEM)
        val lock = shadow.getBitmap(WallpaperManager.FLAG_LOCK)
        assertNotNull(home)
        assertNotNull(lock)
        assertNotSame(lock, home)
        // The home screen's bottom row is exactly the palette background.
        val bg = store.currentOrCreate().colors[0]
        assertEquals(bg, home!!.getPixel(home.width / 2, home.height - 1))
    }

    @Test
    fun fadeIsSolidAtBottomAndGentleAboveTheStart() {
        val art = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        art.eraseColor(Color.RED)
        val out = Wallpaper.fade(art, Color.GREEN, 15)   // fade starts at y = 170
        assertEquals(Color.RED, out.getPixel(50, 5))
        assertEquals(Color.RED, out.getPixel(50, 168))
        assertEquals(Color.GREEN, out.getPixel(50, 199))
        val mid = out.getPixel(50, 185)
        assertTrue("mid-fade should blend, got ${Integer.toHexString(mid)}",
            mid != Color.RED && mid != Color.GREEN)

        val untouched = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        assertSame(untouched, Wallpaper.fade(untouched, Color.GREEN, 0))
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
