package uk.co.siland.culvery.capability.party

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
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
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.SceneChoice
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.soon

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PartySettingsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun open(scope: CoroutineScope) =
        PartySettings(settingsDataStore(scope) { File(folder.root, "party_settings.preferences_pb") }, scope)

    /** 13 §4.3: fails if a restart mid-party loses a choice, the record, or the order of a group's members. */
    @Test
    fun everythingSurvivesARestart() = runTest {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        soon {
            val settings = open(first)
            settings.update { FULL_PREFS }
            settings.save(FULL_RECORD)
        }
        first.coroutineContext[Job]!!.cancelAndJoin()

        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = open(second)
        assertThat(soon { settings.prefs.first() }).isEqualTo(FULL_PREFS)
        assertThat(soon { settings.record.filterNotNull().first() }).isEqualTo(FULL_RECORD)
        second.cancel()
    }

    /** 13 §2.4: fails if "every speaker" is stored as a list, or reads back as one. */
    @Test
    fun everySpeakerIsNoRoomList() = runTest {
        val store = MemoryStore()
        val settings = PartySettings(store, backgroundScope)

        settings.update { it.copy(music = PARTY_MIX, rooms = listOf(KITCHEN)) }
        settings.update { it.copy(rooms = null) }

        assertThat(store.state.value!!.asMap().keys.map { it.name }).doesNotContain("music.rooms")
        assertThat(settings.prefs.first()).isEqualTo(PartyPrefs(music = PARTY_MIX))
    }

    /** Fails if choosing no music drops the speaker rooms, or saving rooms needs a pick. */
    @Test
    fun theRoomsAreKeptWithNoPick() = runTest {
        val settings = PartySettings(MemoryStore(), backgroundScope)

        settings.update { it.copy(rooms = listOf(KITCHEN, LOUNGE)) }
        assertThat(settings.prefs.first()).isEqualTo(PartyPrefs(rooms = listOf(KITCHEN, LOUNGE)))

        settings.update { it.copy(music = PARTY_MIX) }
        settings.update { it.copy(music = null) }

        assertThat(settings.prefs.first()).isEqualTo(PartyPrefs(rooms = listOf(KITCHEN, LOUNGE)))
    }

    /** Fails if a name holding the field or list separators splits into the wrong fields on the way back. */
    @Test
    fun namesWithSeparatorsReadBack() = runTest {
        val odd = "Kitchen = Diner; 2, upstairs"
        val settings = PartySettings(MemoryStore(), backgroundScope)
        val prefs = PartyPrefs(
            music = MusicChoice("FV:2/3", odd),
            rooms = listOf(HouseRoom("Kitchen-id", odd)),
            scenes = listOf(SceneChoice("r1", odd, "s1", odd)),
        )
        val record = FULL_RECORD.copy(lights = listOf(LIGHTS_BEFORE[0].copy(name = odd)))

        settings.update { prefs }
        settings.save(record)

        assertThat(settings.prefs.first()).isEqualTo(prefs)
        runCurrent()
        assertThat(settings.record.value).isEqualTo(record)
    }

    /** 13 §4.3: fails if turning off wipes the family's choices. */
    @Test
    fun clearingTheRecordKeepsTheChoices() = runTest {
        val settings = PartySettings(MemoryStore(), backgroundScope)
        settings.update { FULL_PREFS }
        settings.save(FULL_RECORD)

        settings.clearRecord()

        runCurrent()
        assertThat(settings.record.value).isEqualTo(PartyRecord())
        assertThat(settings.prefs.first()).isEqualTo(FULL_PREFS)
    }

    /** Fails if choosing in Settings during a party forgets what to put back. */
    @Test
    fun updatingTheChoicesKeepsTheRecord() = runTest {
        val settings = PartySettings(MemoryStore(), backgroundScope)
        settings.save(FULL_RECORD)

        settings.update { FULL_PREFS }

        runCurrent()
        assertThat(settings.record.value).isEqualTo(FULL_RECORD)
        assertThat(settings.prefs.first()).isEqualTo(FULL_PREFS)
    }

    /** Fails if a failed record write is swallowed, since the house retries on the IOException. */
    @Test
    fun aFailedWriteThrowsAndLeavesTheOldRecord() = runTest {
        val store = MemoryStore()
        val settings = PartySettings(store, backgroundScope)
        settings.save(PartyRecord(on = true, since = 1))
        store.failOnWrite = 1

        val threw = runCatching { settings.save(FULL_RECORD) }.exceptionOrNull()

        assertThat(threw).isInstanceOf(IOException::class.java)
        runCurrent()
        assertThat(settings.record.value).isEqualTo(PartyRecord(on = true, since = 1))
    }

    /** Fails if the record reads as off before the store has answered. */
    @Test
    fun recordIsNullUntilTheStoreAnswers() = runTest {
        val store = MemoryStore(initial = null)
        val settings = PartySettings(store, backgroundScope)
        runCurrent()
        assertThat(settings.record.value).isNull()

        store.state.value = emptyPreferences()
        runCurrent()
        assertThat(settings.record.value).isEqualTo(PartyRecord())
    }

    @Test
    fun withSceneReplacesThatRoomsChoice() {
        val relax = KITCHEN_BRIGHT.copy(sceneId = "s2", sceneName = "Relax")

        val prefs = FULL_PREFS.withScene(relax)

        assertThat(prefs.scenes).containsExactly(LOUNGE_RELAX, relax).inOrder()
    }

    @Test
    fun withoutSceneRemovesIt() {
        assertThat(FULL_PREFS.withoutScene("r1").scenes).containsExactly(LOUNGE_RELAX)
        assertThat(FULL_PREFS.withoutScene("nowhere").scenes).isEqualTo(FULL_PREFS.scenes)
    }

    /** Fails if one bad value throws, or takes its good neighbours down with it. */
    @Test
    fun malformedValuesAreSkipped() = runTest {
        val sep = "\u001F"
        val store = MemoryStore(
            mutablePreferencesOf(
                stringSetPreferencesKey("scenes") to setOf("r1${sep}Kitchen${sep}s1${sep}Bright", "r1${sep}Kitchen${sep}s1"),
                stringSetPreferencesKey("lights.before") to setOf(
                    "r1${sep}Kitchen${sep}s1${sep}maybe${sep}40${sep}s2",
                    "r2${sep}Lounge${sep}s3${sep}false${sep}0${sep}",
                ),
                stringPreferencesKey("music.made") to "Lounge-id",
            ),
        )
        val settings = PartySettings(store, backgroundScope)

        assertThat(settings.prefs.first().scenes).containsExactly(KITCHEN_BRIGHT)
        runCurrent()
        val record = settings.record.value!!
        assertThat(record.lights).containsExactly(LIGHTS_BEFORE[1])
        assertThat(record.made).isNull()
    }

    /** 13 §2.14: fails if a log line holding a choice or the record can show a name or id. */
    @Test
    fun toStringHasNoIdsOrNames() {
        for (text in listOf(FULL_PREFS.toString(), PARTY_MIX.toString(), FULL_RECORD.toString())) {
            for (secret in listOf("Party mix", "FV:2/3", "Kitchen", "Lounge", "r1", "s1", "1000")) {
                assertThat(text).doesNotContain(secret)
            }
        }
    }
}
