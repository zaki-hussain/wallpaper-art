package dev.artwidget

import android.app.AlarmManager
import android.app.WallpaperManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        ArtWidgetProvider.newArtAsync(context) { done.countDown() }
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
