package uk.co.siland.culvery.capability.party

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.soon

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ModesStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val file get() = File(folder.root, "party_settings.preferences_pb")

    /** The file as a store from before 15b left it: written with no move. */
    private suspend fun writeOld(vararg pairs: Preferences.Pair<*>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        soon { settingsDataStore(scope) { file }.edit { it.putAll(*pairs) } }
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    /** The file opened as the app opens it, with the move; [close] before opening it again. */
    private inner class Opened {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val data = settingsDataStore(scope, migrations = listOf(PartyMove)) { file }
        val store = ModesStore(data, scope)

        suspend fun prefs() = soon { store.prefs.first() }

        suspend fun record() = soon { store.record.filterNotNull().first() }

        suspend fun keys(): Map<String, Any> = soon { data.data.first() }.asMap().mapKeys { it.key.name }

        suspend fun close() = scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    /** 15b §4.4: fails if a fresh install has no Party mode, or it starts off Home, without a PIN, or with something to do. */
    @Test
    fun aFreshFileHasPartyModeWithNothingToDo() = runTest {
        val opened = Opened()

        val prefs = opened.prefs()

        assertThat(prefs.modes).containsExactly(PARTY_MODE)
        val party = prefs.modes.single()
        assertThat(party.name).isEqualTo("Party mode")
        assertThat(party.icon).isEqualTo("celebration")
        assertThat(party.showOnHome).isTrue()
        assertThat(party.needsPin).isTrue()
        assertThat(party.music).isNull()
        assertThat(party.speakers).isNull()
        assertThat(party.lights).isEmpty()
        assertThat(prefs.holidayOnHome).isTrue()
        assertThat(opened.record()).isEqualTo(ModeRecord())
        opened.close()
    }

    /** 15b §4.4: fails if the music, speakers or a scene is lost in the move, in either form a scene was saved, or the old keys stay. */
    @Test
    fun theMoveKeepsPartysChoices() = runTest {
        writeOld(
            OLD_PICK to "FV:2/3",
            OLD_PICK_NAME to "Party mix",
            OLD_ROOMS to setOf("Kitchen-id${SEP}Kitchen", "Lounge-id${SEP}Lounge"),
            OLD_SCENES to setOf("r1${SEP}Kitchen${SEP}s1${SEP}Bright", "r2${SEP}Lounge${SEP}scene${SEP}s3${SEP}Relax$SEP"),
        )
        val opened = Opened()

        val party = opened.prefs().mode(HouseModes.PARTY)!!

        assertThat(party).isEqualTo(FULL_MODE)
        assertThat(party.lights).containsExactly(
            RoomChoice("r1", "Kitchen", LightChoice.Scene("s1", "Bright")),
            RoomChoice("r2", "Lounge", LightChoice.Scene("s3", "Relax")),
        )
        assertThat(opened.keys().keys).containsNoneOf("music.pick", "music.pick_name", "music.rooms", "scenes")
        opened.close()
    }

    /** Review Focus 1: fails if Party on during the move loses its record, or the record isn't Party's. */
    @Test
    fun theMoveKeepsAnOnRecord() = runTest {
        writeOld(
            OLD_ON to true,
            OLD_SINCE to 1_000L,
            OLD_MADE to "Lounge-id${SEP}Kitchen-id,Lounge-id,Dining-id",
            OLD_BEFORE to setOf("Kitchen-id${SEP}Kitchen-id", "Lounge-id${SEP}Lounge-id,Dining-id"),
            OLD_LIGHTS to setOf("r1${SEP}Kitchen${SEP}s1${SEP}true${SEP}40${SEP}s2", "r2${SEP}Lounge${SEP}s3${SEP}false${SEP}0$SEP"),
        )
        val opened = Opened()

        assertThat(opened.record()).isEqualTo(FULL_RECORD)
        assertThat(opened.record().lights!!.map { it.choice }).containsExactly(LightChoice.Scene("s1", ""), LightChoice.Scene("s3", ""))
        opened.close()
    }

    /** Ruling 5: fails if one bad old entry stops the move, takes the good ones with it, or turns Party off. */
    @Test
    fun aCorruptScenesEntryDoesntStopTheMove() = runTest {
        writeOld(
            OLD_ON to true,
            OLD_SCENES to setOf("r1${SEP}Kitchen${SEP}s1${SEP}Bright", "r1${SEP}Kitchen${SEP}s1", "r2${SEP}Lounge${SEP}scene${SEP}s3${SEP}Relax$SEP"),
        )
        val opened = Opened()

        assertThat(opened.prefs().mode(HouseModes.PARTY)!!.lights).containsExactly(KITCHEN_BRIGHT, LOUNGE_RELAX)
        assertThat(opened.record().on).isTrue()
        assertThat(opened.record().modeId).isEqualTo(HouseModes.PARTY)
        opened.close()
    }

    /** Fails if a record that is off is moved as a mode's, or loses the speakers still to put back. */
    @Test
    fun anOffRecordMovesWithNoMode() = runTest {
        writeOld(OLD_BEFORE to setOf("Kitchen-id${SEP}Kitchen-id", "Lounge-id${SEP}Lounge-id,Dining-id"))
        val opened = Opened()

        assertThat(opened.record()).isEqualTo(ModeRecord(musicBefore = GROUPS_BEFORE))
        assertThat(opened.keys().keys).doesNotContain("record.mode")
        opened.close()
    }

    /** Ruling 5: fails if Party mode, once deleted, comes back at the next start. */
    @Test
    fun theMoveRunsOnce() = runTest {
        val first = Opened()
        first.prefs()
        first.store.update { it.copy(modes = emptyList()) }
        first.close()

        val second = Opened()

        assertThat(second.prefs().modes).isEmpty()
        second.close()
    }

    /** 13 §4.3: fails if a restart mid-mode loses a choice, a flag, the record, or the order of a group's members. */
    @Test
    fun everyFieldRoundTrips() = runTest {
        val film = StoredMode(
            id = "modeabcdef",
            name = "Film night",
            icon = "movie",
            showOnHome = false,
            needsPin = false,
            speakers = listOf(LOUNGE),
            lights = listOf(RoomChoice("r2", "Lounge", LightChoice.On(40)), RoomChoice("r3", "Hall", LightChoice.Off)),
        )
        val prefs = ModesPrefs(holidayOnHome = false, modes = listOf(FULL_MODE, film))
        val record = FULL_RECORD.copy(
            modeId = "modeabcdef",
            lights = LIGHTS_BEFORE + RoomBefore("r3", "Hall", LightChoice.Off, false, 0, null) + RoomBefore("r4", "Den", LightChoice.On(40), true, 100, null),
        )
        val first = Opened()
        first.store.update { prefs }
        first.store.save(record)
        first.close()

        val second = Opened()

        assertThat(second.prefs()).isEqualTo(prefs)
        assertThat(second.prefs().mode(HouseModes.PARTY)!!.speakers).isEqualTo(listOf(KITCHEN, LOUNGE))
        assertThat(second.prefs().mode("modeabcdef")!!.music).isNull()
        assertThat(second.record()).isEqualTo(record)
        second.close()
    }

    /** Ruling 5: fails if deleting every mode leaves no `modes` key, so the move would bring Party back, or a blank id reads as a mode. */
    @Test
    fun noModesWritesAnEmptyKey() = runTest {
        val opened = Opened()
        opened.store.update { it.copy(modes = emptyList()) }

        assertThat(opened.keys()["modes"]).isEqualTo("")
        assertThat(opened.keys().keys.filter { it.startsWith("mode.") }).isEmpty()
        opened.close()

        writeOld(MODES to " ,party,,", stringPreferencesKey("mode.party.name") to "Party mode", stringPreferencesKey("mode.party.icon") to PARTY_ICON)
        val again = Opened()
        assertThat(again.prefs().modes.map { it.id }).containsExactly(HouseModes.PARTY)
        again.close()
    }

    /** Fails if a mode listed with no name throws, or takes the other modes with it. */
    @Test
    fun aCorruptModeEntryIsSkipped() = runTest {
        writeOld(
            MODES to "modeaaaaaa,party",
            stringPreferencesKey("mode.modeaaaaaa.icon") to "movie",
            stringPreferencesKey("mode.party.name") to "Party mode",
            stringPreferencesKey("mode.party.icon") to PARTY_ICON,
        )
        val opened = Opened()

        assertThat(opened.prefs().modes.map { it.id }).containsExactly(HouseModes.PARTY)
        opened.close()
    }

    /** 13 §2.4: fails if "every speaker" is stored as a list, or reads back as one. */
    @Test
    fun everySpeakerIsNoRoomList() = runTest {
        val store = MemoryStore()
        val settings = ModesStore(store, backgroundScope)

        settings.update { it.withMode(HouseModes.PARTY) { m -> m.copy(music = PARTY_MIX, speakers = listOf(KITCHEN)) } }
        settings.update { it.withMode(HouseModes.PARTY) { m -> m.copy(speakers = null) } }

        assertThat(store.state.value!!.asMap().keys.map { it.name }).doesNotContain("mode.party.rooms")
        assertThat(settings.prefs.first().mode(HouseModes.PARTY)).isEqualTo(PARTY_MODE.copy(music = PARTY_MIX))
    }

    /** Fails if choosing no music drops the speaker rooms, or saving rooms needs a pick. */
    @Test
    fun theRoomsAreKeptWithNoPick() = runTest {
        val settings = ModesStore(MemoryStore(), backgroundScope)
        val rooms = PARTY_MODE.copy(speakers = listOf(KITCHEN, LOUNGE))

        settings.update { it.withMode(HouseModes.PARTY) { rooms } }
        assertThat(settings.prefs.first().mode(HouseModes.PARTY)).isEqualTo(rooms)

        settings.update { it.withMode(HouseModes.PARTY) { m -> m.copy(music = PARTY_MIX) } }
        settings.update { it.withMode(HouseModes.PARTY) { m -> m.copy(music = null) } }

        assertThat(settings.prefs.first().mode(HouseModes.PARTY)).isEqualTo(rooms)
    }

    /** Fails if a name holding the field or list separators splits into the wrong fields on the way back. */
    @Test
    fun namesWithSeparatorsReadBack() = runTest {
        val odd = "Kitchen = Diner; 2, upstairs"
        val settings = ModesStore(MemoryStore(), backgroundScope)
        val prefs = ModesPrefs(
            modes = listOf(
                PARTY_MODE.copy(
                    name = odd,
                    music = MusicChoice("FV:2/3", odd),
                    speakers = listOf(HouseRoom("Kitchen-id", odd)),
                    lights = listOf(RoomChoice("r1", odd, LightChoice.Scene("s1", odd))),
                ),
            ),
        )
        val record = FULL_RECORD.copy(lights = listOf(LIGHTS_BEFORE[0].copy(name = odd)))

        settings.update { prefs }
        settings.save(record)

        assertThat(settings.prefs.first()).isEqualTo(prefs)
        runCurrent()
        assertThat(settings.record.value).isEqualTo(record)
    }

    /** Fails if setting a mode's PIN writes any key but that mode's own, or a mode that isn't stored gains keys. */
    @Test
    fun setNeedsPinWritesOnlyThatModesPinKey() = runTest {
        val memory = MemoryStore()
        val settings = ModesStore(memory, backgroundScope)
        settings.update { ModesPrefs(modes = listOf(FULL_MODE, FULL_MODE.copy(id = "modeabcdef", name = "Film night"))) }
        settings.save(FULL_RECORD)
        val before = memory.state.value!!.asMap().mapKeys { it.key.name }

        settings.setNeedsPin("modeabcdef", false)
        settings.setNeedsPin("nowhere", false)

        val after = memory.state.value!!.asMap().mapKeys { it.key.name }
        val changed = (before.keys + after.keys).filter { before[it] != after[it] }
        assertThat(changed).containsExactly(ModeKeys.For("modeabcdef").pin.name)
        assertThat(settings.prefs.first().mode("modeabcdef")!!.needsPin).isFalse()
        assertThat(settings.prefs.first().mode(PARTY_MODE.id)!!.needsPin).isTrue()
    }

    /** Fails if a write made from a copy that says off over a stored on drops the PIN, or can't write one that is stored off. */
    @Test
    fun updateKeepsAStoredPinOn() = runTest {
        val settings = ModesStore(MemoryStore(), backgroundScope)
        val film = FULL_MODE.copy(id = "modeabcdef", name = "Film night", needsPin = false)
        settings.update { ModesPrefs(modes = listOf(FULL_MODE, film)) }

        settings.update { it.copy(modes = it.modes.map { m -> m.copy(needsPin = false, showOnHome = false) }) }

        val modes = settings.prefs.first().modes
        assertThat(modes.map { it.needsPin }).containsExactly(true, false).inOrder()
        assertThat(modes.map { it.showOnHome }).containsExactly(false, false)
        settings.update { it.copy(modes = it.modes.map { m -> m.copy(needsPin = true) }) }
        assertThat(settings.prefs.first().modes.map { it.needsPin }).containsExactly(true, true)
    }

    /** Regression guard: renaming a mode that needs a PIN keeps it. */
    @Test
    fun aRenameOfAModeThatNeedsAPinKeepsIt() = runTest {
        val settings = ModesStore(MemoryStore(), backgroundScope)
        settings.update { FULL_PREFS }

        settings.update { it.withMode(FULL_MODE.id) { m -> m.copy(name = "Disco") } }

        val mode = settings.prefs.first().mode(FULL_MODE.id)!!
        assertThat(mode.name).isEqualTo("Disco")
        assertThat(mode.needsPin).isTrue()
    }

    /** 13 §4.3: fails if turning off wipes the family's choices. */
    @Test
    fun clearingTheRecordKeepsTheChoices() = runTest {
        val settings = ModesStore(MemoryStore(), backgroundScope)
        settings.update { FULL_PREFS }
        settings.save(FULL_RECORD)

        settings.clearRecord()

        runCurrent()
        assertThat(settings.record.value).isEqualTo(ModeRecord())
        assertThat(settings.prefs.first()).isEqualTo(FULL_PREFS)
    }

    /** Fails if choosing in Settings during a mode forgets what to put back. */
    @Test
    fun updatingTheChoicesKeepsTheRecord() = runTest {
        val settings = ModesStore(MemoryStore(), backgroundScope)
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
        val settings = ModesStore(store, backgroundScope)
        settings.save(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1))
        store.failOnWrite = 1

        val threw = runCatching { settings.save(FULL_RECORD) }.exceptionOrNull()

        assertThat(threw).isInstanceOf(IOException::class.java)
        runCurrent()
        assertThat(settings.record.value).isEqualTo(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1))
    }

    /** Fails if the record reads as off before the store has answered. */
    @Test
    fun recordIsNullUntilTheStoreAnswers() = runTest {
        val store = MemoryStore(initial = null)
        val settings = ModesStore(store, backgroundScope)
        runCurrent()
        assertThat(settings.record.value).isNull()

        store.state.value = emptyPreferences()
        runCurrent()
        assertThat(settings.record.value).isEqualTo(ModeRecord())
    }

    /** F7: fails if a changed room moves from its place, or a new one doesn't go last. */
    @Test
    fun withLightReplacesThatRoomsChoice() {
        val relax = KITCHEN_BRIGHT.copy(choice = LightChoice.Scene("s2", "Relax"))
        val hall = RoomChoice("r3", "Hall", LightChoice.Off)

        assertThat(FULL_MODE.withLight(relax).lights).containsExactly(relax, LOUNGE_RELAX).inOrder()
        assertThat(FULL_MODE.withLight(hall).lights).containsExactly(KITCHEN_BRIGHT, LOUNGE_RELAX, hall).inOrder()
    }

    @Test
    fun withoutLightRemovesIt() {
        assertThat(FULL_MODE.withoutLight("r1").lights).containsExactly(LOUNGE_RELAX)
        assertThat(FULL_MODE.withoutLight("nowhere").lights).isEqualTo(FULL_MODE.lights)
    }

    @Test
    fun withModeChangesOnlyThatMode() {
        val other = PARTY_MODE.copy(id = "modeabcdef", name = "Film night")
        val prefs = ModesPrefs(modes = listOf(PARTY_MODE, other))

        val next = prefs.withMode("modeabcdef") { it.copy(needsPin = false) }

        assertThat(next.modes).containsExactly(PARTY_MODE, other.copy(needsPin = false)).inOrder()
        assertThat(prefs.withMode("nowhere") { it.copy(needsPin = false) }).isEqualTo(prefs)
    }

    /** Fails if one bad value throws, or takes its good neighbours down with it. */
    @Test
    fun malformedValuesAreSkipped() = runTest {
        val store = MemoryStore(
            mutablePreferencesOf(
                MODES to HouseModes.PARTY,
                stringPreferencesKey("mode.party.name") to "Party mode",
                stringPreferencesKey("mode.party.icon") to PARTY_ICON,
                stringSetPreferencesKey("mode.party.lights") to setOf(
                    "r1${SEP}Kitchen${SEP}scene${SEP}s1${SEP}Bright$SEP",
                    "r1${SEP}Kitchen${SEP}s1",
                    "r2${SEP}Lounge${SEP}dim${SEP}${SEP}${SEP}40",
                    "r2${SEP}Lounge${SEP}on${SEP}${SEP}${SEP}lots",
                ),
                OLD_LIGHTS to setOf(
                    "r1${SEP}Kitchen${SEP}s1${SEP}maybe${SEP}40${SEP}s2",
                    "r2${SEP}Lounge${SEP}s3${SEP}false${SEP}0$SEP",
                ),
                OLD_MADE to "Lounge-id",
            ),
        )
        val settings = ModesStore(store, backgroundScope)

        assertThat(settings.prefs.first().mode(HouseModes.PARTY)!!.lights).containsExactly(KITCHEN_BRIGHT)
        runCurrent()
        val record = settings.record.value!!
        assertThat(record.lights).containsExactly(LIGHTS_BEFORE[1])
        assertThat(record.made).isNull()
    }

    /** 13 §2.14, 15b §5: fails if a log line holding a mode, a choice or the record can show a name or id. */
    @Test
    fun toStringHasNoIdsOrNames() {
        val film = FULL_RECORD.copy(modeId = "modeabcdef")
        for (text in listOf(FULL_PREFS.toString(), FULL_MODE.toString(), PARTY_MIX.toString(), FULL_RECORD.toString(), film.toString())) {
            for (secret in listOf("Party mix", "Party mode", "party", "modeabcdef", "celebration", "FV:2/3", "Kitchen", "Lounge", "r1", "s1", "1000")) {
                assertThat(text).doesNotContain(secret)
            }
        }
    }

    /** 15b §4.2: fails if a six-field record entry written before 15b is dropped, or reads as anything but a scene. */
    @Test
    fun aPreFifteenBRecordReadsAsScenes() = runTest {
        val store = MemoryStore(mutablePreferencesOf(OLD_LIGHTS to setOf("r1${SEP}Kitchen${SEP}s1${SEP}true${SEP}40${SEP}s2")))
        val settings = ModesStore(store, backgroundScope)

        runCurrent()
        val room = settings.record.value!!.lights!!.single()
        assertThat(room.choice).isInstanceOf(LightChoice.Scene::class.java)
        assertThat((room.choice as LightChoice.Scene).sceneId).isEqualTo("s1")
        assertThat(room.on).isTrue()
        assertThat(room.activeSceneId).isEqualTo("s2")
    }

    /** 15b §4.2: fails if On's percent or Off is lost, in the choices or in the record. */
    @Test
    fun aRoomChoiceRoundTrips() = runTest {
        val settings = ModesStore(MemoryStore(), backgroundScope)
        val choices = listOf(LightChoice.Scene("s1", "Bright"), LightChoice.On(60), LightChoice.Off)
        val mode = PARTY_MODE.copy(lights = choices.mapIndexed { i, c -> RoomChoice("r$i", "Room $i", c) })
        val record = ModeRecord(modeId = HouseModes.PARTY, on = true, lights = choices.mapIndexed { i, c -> RoomBefore("r$i", "Room $i", c, true, 40, "s2") })

        settings.update { ModesPrefs(modes = listOf(mode)) }
        settings.save(record)

        assertThat(settings.prefs.first().mode(HouseModes.PARTY)).isEqualTo(mode)
        val back = settings.record.filterNotNull().first { it.lights != null }.lights!!
        assertThat(back.map { it.choice }).containsExactly(LightChoice.Scene("s1", ""), LightChoice.On(60), LightChoice.Off).inOrder()
        assertThat(back.map { it.activeSceneId }.distinct()).containsExactly("s2")
    }

    private companion object {
        const val SEP = "\u001F"
        val MODES = stringPreferencesKey("modes")
        val OLD_PICK = stringPreferencesKey("music.pick")
        val OLD_PICK_NAME = stringPreferencesKey("music.pick_name")
        val OLD_ROOMS = stringSetPreferencesKey("music.rooms")
        val OLD_SCENES = stringSetPreferencesKey("scenes")
        val OLD_ON = booleanPreferencesKey("on")
        val OLD_SINCE = longPreferencesKey("since")
        val OLD_MADE = stringPreferencesKey("music.made")
        val OLD_BEFORE = stringSetPreferencesKey("music.before")
        val OLD_LIGHTS = stringSetPreferencesKey("lights.before")
    }
}
