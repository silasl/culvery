package uk.co.siland.culvery.capability.holiday

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.soon

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HolidaySettingsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun open(scope: CoroutineScope) =
        HolidaySettings(settingsDataStore(scope) { File(folder.root, "holiday_settings.preferences_pb") }, scope)

    private val away = HolidayRecord(
        on = true,
        back = LocalDate.of(2026, 10, 31),
        since = 1_793_000_000_000L,
        steps = mapOf(HolidayStep.LIGHTS_ON to StepState.DONE, HolidayStep.ECO_ON to StepState.PENDING, HolidayStep.HOOVER to StepState.MISSED),
    )

    /** 11 §5.7: fails if a restart mid-holiday loses the record, the back date or a step's state. */
    @Test
    fun theRecordSurvivesARestart() = runTest {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        soon { open(first).save(away) }
        first.coroutineContext[Job]!!.cancelAndJoin()

        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = open(second)
        assertThat(soon { settings.read.filterNotNull().first() }).isEqualTo(away)
        second.cancel()
    }

    /** Fails if the record reads as off before the store has answered, which would let the door chime on a holiday. */
    @Test
    fun readIsNullUntilTheStoreAnswers() = runTest {
        val store = MemoryStore(initial = null)
        val settings = HolidaySettings(store, backgroundScope)
        runCurrent()
        assertThat(settings.read.value).isNull()

        store.state.value = emptyPreferences()
        runCurrent()

        assertThat(settings.read.value).isEqualTo(HolidayRecord())
    }

    /** Fails if turning off leaves anything of the holiday behind. */
    @Test
    fun clearEmptiesIt() = runTest {
        val store = MemoryStore()
        val settings = HolidaySettings(store, backgroundScope)
        settings.save(away)
        assertThat(settings.prefs.first()).isEqualTo(away)

        settings.clear()

        assertThat(settings.prefs.first()).isEqualTo(HolidayRecord())
        assertThat(store.state.value!!.asMap()).isEmpty()
    }

    /** 11 §6: fails if the back date, or when it was turned on, can be read out of a log line. */
    @Test
    fun toStringHasNoDate() {
        val text = away.toString()
        assertThat(text).doesNotContain("2026")
        assertThat(text).doesNotContain("31")
        assertThat(text).doesNotContain("1793")
        assertThat(text).contains("on=true")
    }

    /** Fails if a step state written by a later version stops the rest of the record being read. */
    @Test
    fun anUnknownStepIsSkipped() = runTest {
        val store = MemoryStore()
        val settings = HolidaySettings(store, backgroundScope)
        settings.save(away)
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                val key = stringSetPreferencesKey("steps")
                this[key] = this[key].orEmpty() + "SAUNA=DONE" + "ECO_OFF=LATER"
            }
        }

        assertThat(settings.prefs.first()).isEqualTo(away)
    }
}
