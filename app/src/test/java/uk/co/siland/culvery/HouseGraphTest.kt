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
import uk.co.siland.culvery.core.house.HouseModeScheduler
import uk.co.siland.culvery.di.HouseGraphProbe
import uk.co.siland.culvery.provider.lights_hue.HUE_PROVIDER_ID
import uk.co.siland.culvery.provider.music_sonos.SONOS_PROVIDER_ID

/** The real Hilt graph, built by the real application, gives Holiday mode what it needs (11 §4.2); reads it through the debug source set's [HouseGraphProbe]. */
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
    fun thePlugsAreInTheHouse() {
        assertThat(probe.plugs().isPresent).isTrue()
        val holiday = probe.modes().single { it.id == "holiday" }
        assertThat(holiday.permission).isEqualTo("holiday.mode")
    }

    @Test
    fun theAppStartsTheScheduler() = runBlocking {
        assertThat(probe.startables().filterIsInstance<HouseModeScheduler>()).hasSize(1)
        // Nothing else reads the record: only the scheduler's own start, from CulveryApp.onCreate, makes it known.
        assertThat(withTimeout(10_000) { probe.houseMode().holiday.first { it != null } }).isFalse()
    }

    @Test
    fun partyIsInTheHouse() {
        val party = probe.modes().single { it.id == "party" }
        assertThat(party.permission).isEqualTo("party.mode")
        assertThat(party.order).isEqualTo(20)
        assertThat(probe.switches().map { it.modeId }).containsExactly("holiday", "party")
        assertThat(probe.music().isPresent).isTrue()
    }

    @Test
    fun theAppStartsPartysSchedulerAndItLoadsTheRecord() = runBlocking {
        // Nothing else reads the record: only the scheduler's own start, from CulveryApp.onCreate, makes it known (13 §4.3).
        assertThat(withTimeout(10_000) { probe.partyHouse().isOn.first { it != null } }).isFalse()
    }

    /** 13 §8: the debug samples are bound, yet Find and a typed address ask the real providers. */
    @Test
    fun theSamplesAreNeverWhatFindAsks() {
        assertThat(probe.speakers().providers.map { it.descriptor.id }).contains("sample.music")
        assertThat(probe.speakers().connectable?.descriptor?.id).isEqualTo(SONOS_PROVIDER_ID)
        assertThat(probe.bridges().providers.map { it.descriptor.id }).contains("sample.lights")
        assertThat(probe.bridges().connectable?.descriptor?.id).isEqualTo(HUE_PROVIDER_ID)
    }
}
