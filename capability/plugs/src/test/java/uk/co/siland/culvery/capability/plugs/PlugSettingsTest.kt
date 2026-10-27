package uk.co.siland.culvery.capability.plugs

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.soon

@RunWith(AndroidJUnit4::class)
class PlugSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun open(scope: CoroutineScope) =
        PlugSettings(settingsDataStore(scope) { File(folder.root, "plug_settings.preferences_pb") })

    private val full = PlugPrefs(
        plugs = listOf("switch.lamp", "switch.fan"),
        needsPin = setOf("switch.fan"),
        modes = mapOf("holiday" to mapOf("switch.lamp" to false)),
        runs = mapOf("holiday" to ModeRun(place = 3, pending = mapOf("switch.lamp" to false))),
        notes = mapOf("switch.lamp" to PlugNote(true, listOf(Holder("holiday", false), Holder("party", true)))),
        next = 4,
    )

    /** 12 §5.2: fails if a restart loses a list, a PIN setting, a run, or a note's before or order of holders. */
    @Test
    fun everythingSurvivesARestart() = runTest {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        soon { open(first).update { full } }
        first.coroutineContext[Job]!!.cancelAndJoin()

        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val read = soon { open(second).prefs.first() }
        assertThat(read).isEqualTo(full)
        assertThat(read.plugs).containsExactly("switch.lamp", "switch.fan").inOrder()
        assertThat(read.notes.getValue("switch.lamp").expected).isTrue()
        second.cancel()
    }

    /** 12 §2.4: fails if a new plug, or a mode not yet seen, starts on anything but Leave. */
    @Test
    fun aNewPlugIsLeaveInEveryMode() {
        val prefs = PlugPrefs().with("holiday", "switch.lamp", ModeChoice.ON).adding("switch.desk")

        assertThat(prefs.choice("holiday", "switch.desk")).isEqualTo(ModeChoice.LEAVE)
        assertThat(prefs.choice("party", "switch.desk")).isEqualTo(ModeChoice.LEAVE)
        assertThat(prefs.choice("holiday", "switch.lamp")).isEqualTo(ModeChoice.ON)
    }

    @Test
    fun settingLeaveRemovesTheEntry() {
        val on = PlugPrefs().with("holiday", "switch.lamp", ModeChoice.OFF).with("holiday", "switch.fan", ModeChoice.ON)
        assertThat(on.choice("holiday", "switch.lamp")).isEqualTo(ModeChoice.OFF)

        val left = on.with("holiday", "switch.lamp", ModeChoice.LEAVE)
        assertThat(left.modes).isEqualTo(mapOf("holiday" to mapOf("switch.fan" to true)))
        assertThat(left.with("holiday", "switch.fan", ModeChoice.LEAVE).modes).isEmpty()
    }

    /** 12 §5.2: fails if a removed plug leaves a trace in any field, or its note key in the file. */
    @Test
    fun removeDropsItEverywhere() = runTest {
        val store = MemoryStore()
        val settings = PlugSettings(store)
        settings.update { full.with("holiday", "switch.fan", ModeChoice.ON) }

        settings.update { it.without("switch.lamp") }

        val prefs = settings.prefs.first()
        assertThat(prefs.plugs).containsExactly("switch.fan")
        assertThat(prefs.needsPin).containsExactly("switch.fan")
        assertThat(prefs.modes).isEqualTo(mapOf("holiday" to mapOf("switch.fan" to true)))
        assertThat(prefs.runs).isEqualTo(mapOf("holiday" to ModeRun(3, emptyMap())))
        assertThat(prefs.notes).isEmpty()
        assertThat(store.state.value!!.asMap().keys.map { it.name }).doesNotContain("note.switch.lamp")
        assertThat(store.state.value!!.asMap().values.toString()).doesNotContain("switch.lamp")
    }

    @Test
    fun invalidIdsAreIgnored() {
        val prefs = PlugPrefs(plugs = listOf("switch.lamp"))

        assertThat(prefs.adding("sensor.lamp")).isEqualTo(prefs)
        assertThat(prefs.adding("switch.Lamp")).isEqualTo(prefs)
        assertThat(prefs.adding("switch.lamp,fan")).isEqualTo(prefs)
        assertThat(prefs.with("Holiday", "switch.lamp", ModeChoice.ON)).isEqualTo(prefs)
        assertThat(prefs.with("holiday", "sensor.lamp", ModeChoice.ON)).isEqualTo(prefs)
    }

    /** Fails if one bad value throws, or stops the rest of the file being read. */
    @Test
    fun malformedValuesAreSkipped() = runTest {
        val store = MemoryStore(
            emptyPreferences().toMutablePreferences().apply {
                this[stringPreferencesKey("plugs")] = ""
                this[stringPreferencesKey("note.switch.lamp")] = "maybe;x"
                this[stringSetPreferencesKey("mode.holiday")] = setOf("switch.fan=perhaps")
                this[stringPreferencesKey("started.holiday")] = "yesterday"
                this[stringSetPreferencesKey("pending.holiday")] = setOf("=on")
            },
        )

        assertThat(PlugSettings(store).prefs.first()).isEqualTo(PlugPrefs())
    }

    /** 12 §6: fails if an entity id or a mode id can be read out of a log line. */
    @Test
    fun toStringHasNoIds() {
        val note = PlugNote(true, listOf(Holder("holiday", false)))
        val run = ModeRun(3, mapOf("switch.lamp" to false))
        for (text in listOf(full.toString(), note.toString(), note.holders.first().toString(), run.toString())) {
            assertThat(text).doesNotContain("switch.")
            assertThat(text).doesNotContain("holiday")
            assertThat(text).doesNotContain("party")
        }
    }

    /** 12 §5.2: fails if a store that can't write hides it, so a caller thinks a list saved. */
    @Test
    fun aFailedWriteThrowsAndLeavesTheFileAlone() = runTest {
        val store = MemoryStore().apply { failOnWrite = 1 }
        val settings = PlugSettings(store)

        val failure = runCatching { settings.update { it.adding("switch.lamp") } }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IOException::class.java)
        assertThat(settings.prefs.first().plugs).isEmpty()
        settings.update { it.adding("switch.lamp") }
        assertThat(settings.prefs.first().plugs).containsExactly("switch.lamp")
    }
}
