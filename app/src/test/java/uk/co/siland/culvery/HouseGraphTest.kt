package uk.co.siland.culvery

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import uk.co.siland.culvery.capability.holiday.HolidayScheduler
import uk.co.siland.culvery.di.HouseGraphProbe

/** The real Hilt graph, built by the real application, gives Holiday mode what it needs (11 §4.2). */
@RunWith(AndroidJUnit4::class)
@Config(application = CulveryApp::class)
class HouseGraphTest {
    private val probe: HouseGraphProbe by lazy {
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext<Application>(), HouseGraphProbe::class.java)
    }

    @Test
    fun houseModeIsTheHolidayHouse() {
        assertThat(probe.houseMode()).isSameInstanceAs(probe.holidayHouse())
    }

    @Test
    fun everyDeviceSeamIsPresent() {
        assertThat(probe.lights().isPresent).isTrue()
        assertThat(probe.heating().isPresent).isTrue()
        assertThat(probe.hoover().isPresent).isTrue()
        assertThat(probe.multiDayEvents().isPresent).isTrue()
    }

    @Test
    fun theSchedulerIsStartedWithTheOthersAndReadsTheRecord() = runBlocking {
        val scheduler = probe.startables().filterIsInstance<HolidayScheduler>()
        assertThat(scheduler).hasSize(1)
        scheduler.single().start()
        assertThat(withTimeout(10_000) { probe.houseMode().holiday.first { it != null } }).isFalse()
    }
}
