package uk.co.siland.culvery.capability.heating

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.soon

@RunWith(AndroidJUnit4::class)
class HeatingSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun open(scope: CoroutineScope) = HeatingSettings(settingsDataStore(scope) { File(folder.root, "heating_settings.preferences_pb") })

    /** Fails if a home starts with a thermostat chosen. */
    @Test
    fun nothingIsChosenAtFirst() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        assertThat(soon { open(scope).prefs.first() }).isEqualTo(HeatingPrefs())
        scope.cancel()
    }

    /** Fails if a choice doesn't survive a new store on the same file. */
    @Test
    fun aChoiceSurvivesANewStore() = runTest {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        soon { open(first).choose("climate.hall") }
        first.coroutineContext[Job]!!.cancelAndJoin()
        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = open(second)
        assertThat(soon { settings.prefs.first() }.device).isEqualTo("climate.hall")
        second.cancel()
    }

    /** Fails if None doesn't clear the choice. */
    @Test
    fun noneClearsTheChoice() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = open(scope)
        soon { settings.choose("climate.hall") }
        assertThat(soon { settings.prefs.first { it.device != null } }.device).isEqualTo("climate.hall")
        soon { settings.choose(null) }
        assertThat(soon { settings.prefs.first { it.device == null } }.device).isNull()
        scope.cancel()
    }

    /** Fails if the entity id can be read out of a log line. */
    @Test
    fun toStringHoldsNoId() {
        assertThat(HeatingPrefs("climate.hall").toString()).doesNotContain("climate.hall")
    }
}
