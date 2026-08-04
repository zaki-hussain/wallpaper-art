package dev.artwidget

import android.app.AlarmManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
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
