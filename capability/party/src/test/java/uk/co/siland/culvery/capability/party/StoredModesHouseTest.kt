package uk.co.siland.culvery.capability.party

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.HouseTimings
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.ModeGrant
import uk.co.siland.culvery.core.house.ModeRefusal
import uk.co.siland.culvery.core.house.MusicStart
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.plugin.WallClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class StoredModesHouseTest {
    /** The settings over one store, the three fake seams sharing one call list, and the wall millis the test sets. */
    private class Rig(private val scope: CoroutineScope, val store: MemoryStore = MemoryStore()) {
        val trace = Trace()
        val settings = ModesStore(store, scope)
        val plugs = FakePlugs(trace)
        val music = FakeMusic(trace)
        val lights = FakeLights(trace)
        var wall = 1_000_000L
        val calls get() = trace.calls

        fun house(
            plugs: HousePlugs? = this.plugs,
            music: HouseMusic? = this.music,
            lights: HouseLights? = this.lights,
            settings: ModesStore = this.settings,
        ) = StoredModesHouse(settings, plugs, music, lights, WallClock { wall })

        /** The process starting again: a new store reader over the same file, and a new house. */
        fun restarted(
            plugs: HousePlugs? = this.plugs,
            music: HouseMusic? = this.music,
            lights: HouseLights? = this.lights,
        ) = house(plugs, music, lights, ModesStore(store, scope))

        /** Party mix on every speaker, Bright in Kitchen and Relax in Lounge. */
        suspend fun choose(vararg modes: StoredMode = arrayOf(PARTY_MODE.copy(music = PARTY_MIX, lights = listOf(KITCHEN_BRIGHT, LOUNGE_RELAX)))) =
            settings.update { ModesPrefs(modes = modes.toList()) }

        /** What the file holds now, read by a fresh reader. */
        suspend fun saved(): ModeRecord = ModesStore(store, scope).record.filterNotNull().first()

        fun enters() = calls.count { it == "plugs.enter party" }
    }

    /** What [modeId]'s tile says under it now. */
    private suspend fun StoredModesHouse.lineNow(modeId: String = PARTY): String? = line(modeId).first()

    private fun TestScope.rig() = Rig(backgroundScope)

    private fun by(): ModeGrant = ModeGrant.Person(grant(HousePermissions.STORED_MODE))

    /** Fails if another mode's grant turns Party on, or anything is called or saved before the refusal. */
    @Test
    fun turnOnNeedsItsOwnGrant() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()

        val failure = runCatching { house.turnOn(PARTY, ModeGrant.Person(grant(HousePermissions.HOLIDAY_MODE))) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(rig.calls).isEmpty()
        assertThat(rig.saved()).isEqualTo(ModeRecord())
    }

    /** Fails if the steps run in another order, the begin lacks the grant, or the record lacks a snapshot. */
    @Test
    fun turnOnRunsEachStepInOrder() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        val by = grant(HousePermissions.STORED_MODE)

        house.turnOn(PARTY, ModeGrant.Person(by))

        assertThat(rig.calls).containsExactly(
            "plugs.begin party", "plugs.enter party", "music.before", "music.start FV:2/3 null", "lights.modeBefore", "lights.modeOn",
        ).inOrder()
        assertThat(rig.plugs.begins).containsExactly(by)
        val expected = ModeRecord(modeId = HouseModes.PARTY, on = true, since = rig.wall, musicBefore = GROUPS_BEFORE, made = MADE, lights = LIGHTS_BEFORE)
        assertThat(rig.saved()).isEqualTo(expected)
        assertThat(house.record.value).isEqualTo(expected)
        assertThat(house.isOn.value).isTrue()
    }

    /** Fails if a device is touched before the record reads on. */
    @Test
    fun theRecordIsSavedBeforeAnyDeviceIsTouched() = runTest {
        val rig = rig()
        rig.choose()
        val gate = CompletableDeferred<Unit>()
        rig.trace.gates["plugs.begin"] = gate
        val house = rig.house()

        val turnOn = launch { house.turnOn(PARTY, by()) }
        runCurrent()

        assertThat(rig.calls).containsExactly("plugs.begin party")
        assertThat(rig.saved().on).isTrue()
        gate.complete(Unit)
        turnOn.join()
    }

    /** Review Focus 3: fails if the speakers' groups or the lights aren't saved before the change that needs putting back. */
    @Test
    fun eachSnapshotIsSavedBeforeItsChange() = runTest {
        val music = rig()
        music.choose()
        val musicGate = CompletableDeferred<Unit>()
        music.trace.gates["music.start"] = musicGate
        val musicHouse = music.house()
        val musicTurnOn = launch { musicHouse.turnOn(PARTY, by()) }
        runCurrent()

        assertThat(music.calls.last()).isEqualTo("music.start FV:2/3 null")
        assertThat(music.saved().musicBefore).isEqualTo(GROUPS_BEFORE)
        assertThat(music.saved().made).isNull()
        musicGate.complete(Unit)
        musicTurnOn.join()

        val lights = rig()
        lights.choose()
        val lightsGate = CompletableDeferred<Unit>()
        lights.trace.gates["lights.modeOn"] = lightsGate
        val lightsHouse = lights.house()
        val lightsTurnOn = launch { lightsHouse.turnOn(PARTY, by()) }
        runCurrent()

        assertThat(lights.calls.last()).isEqualTo("lights.modeOn")
        assertThat(lights.saved().lights).isEqualTo(LIGHTS_BEFORE)
        assertThat(lights.saved().made).isEqualTo(MADE)
        lightsGate.complete(Unit)
        lightsTurnOn.join()
    }

    /** 15b §4.3: fails if a mode with nothing to do refuses, calls a seam, or doesn't say so on its tile. */
    @Test
    fun nothingToDoTurnsOnWithTheLine() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        val house = rig.house()

        house.turnOn(PARTY, by())

        assertThat(rig.saved().on).isTrue()
        assertThat(house.onMode.value).isEqualTo(PARTY)
        assertThat(rig.calls).isEmpty()
        assertThat(house.lineNow()).isEqualTo("Nothing to do yet. Choose music, lights or plugs in Settings › Modes.")
    }

    /** Fails if a music system that isn't ready is called or gets a line, though a pick is saved. */
    @Test
    fun aSystemNotReadyIsNoStep() = runTest {
        val rig = rig()
        rig.choose()
        rig.music.readyNow.value = false
        val house = rig.house()

        house.turnOn(PARTY, by())

        assertThat(rig.calls.filter { it.startsWith("music.") }).isEmpty()
        assertThat(house.lineNow()).isNull()
        assertThat(rig.saved().musicBefore).isNull()
    }

    /** Review Focus 2: fails if one step failing or throwing stops another, or the lines aren't the failed ones. */
    @Test
    fun aFailingStepDoesntStopTheOthers() = runTest {
        val rig = rig()
        rig.choose()
        rig.music.started = MusicStart(null, HouseStep.Failed(MUSIC_START_LINE))
        rig.trace.throws += "lights.modeBefore"
        val house = rig.house()

        house.turnOn(PARTY, by())

        assertThat(rig.calls).containsExactly(
            "plugs.begin party", "plugs.enter party", "music.before", "music.start FV:2/3 null", "lights.modeBefore",
        ).inOrder()
        assertThat(house.isOn.value).isTrue()
        assertThat(house.lineNow()).isEqualTo("$MUSIC_START_LINE\nSome lights didn’t change: Kitchen and Lounge.")
    }

    /** Fails if the lines aren't one per kind, music then plugs then lights. */
    @Test
    fun theLinesAreOnePerKindInOrder() = runTest {
        val rig = rig()
        rig.choose()
        rig.music.started = MusicStart(null, HouseStep.Failed(MUSIC_START_LINE))
        rig.plugs.enters += HouseStep.Failed(LAMP_LINE)
        rig.lights.notChanged = listOf("Kitchen")
        val house = rig.house()

        house.turnOn(PARTY, by())

        assertThat(house.lineNow()).isEqualTo("$MUSIC_START_LINE\n$LAMP_LINE\nSome lights didn’t change: Kitchen.")
    }

    /** Fails if the plugs aren't retried at five minutes and no sooner, or music and lights are tried again. */
    @Test
    fun plugsAreRetriedEveryFiveMinutes() = runTest {
        val rig = rig()
        rig.choose()
        rig.plugs.enters += HouseStep.Failed(LAMP_LINE)
        val house = rig.house()
        house.turnOn(PARTY, by())
        val turnedOn = rig.wall
        assertThat(house.lineNow()).isEqualTo(LAMP_LINE)

        rig.wall = turnedOn + HouseTimings.RETRY_MS - 60_000
        house.tick()
        assertThat(rig.enters()).isEqualTo(1)
        rig.wall = turnedOn + HouseTimings.RETRY_MS
        house.tick()

        assertThat(rig.enters()).isEqualTo(2)
        assertThat(house.lineNow()).isNull()
        assertThat(rig.calls.count { it.startsWith("music.start") }).isEqualTo(1)
        assertThat(rig.calls.count { it == "lights.modeOn" }).isEqualTo(1)
        rig.wall = turnedOn + 3 * HouseTimings.RETRY_MS
        house.tick()
        assertThat(rig.enters()).isEqualTo(2)
    }

    /** Fails if an enter that threw shows no line or isn't tried again. */
    @Test
    fun aPlugsStepThatThrowsShowsItsLine() = runTest {
        val rig = rig()
        rig.choose()
        rig.trace.throws += "plugs.enter"
        val house = rig.house()
        house.turnOn(PARTY, by())
        val turnedOn = rig.wall
        assertThat(house.lineNow()).isEqualTo("The plugs didn’t switch. They’ll keep trying.")

        rig.trace.throws -= "plugs.enter"
        rig.wall = turnedOn + HouseTimings.RETRY_MS - 60_000
        house.tick()
        assertThat(rig.enters()).isEqualTo(1)
        rig.wall = turnedOn + HouseTimings.RETRY_MS
        house.tick()

        assertThat(rig.enters()).isEqualTo(2)
        assertThat(house.lineNow()).isNull()
    }

    /** Fails if a begin that threw stops the turn-on, the missing run isn't missed with its own line, or it's tried again. */
    @Test
    fun aPlugsStepWithNoRunIsMissed() = runTest {
        val rig = rig()
        rig.trace.throws += "plugs.begin"
        val house = rig.house()
        house.turnOn(PARTY, by())
        val turnedOn = rig.wall

        assertThat(rig.saved().on).isTrue()
        assertThat(house.lineNow()).isEqualTo("The plugs didn’t switch. Switch them on the Lights tab.")
        rig.wall = turnedOn + 2 * HouseTimings.RETRY_MS
        house.tick()

        assertThat(rig.enters()).isEqualTo(1)
        assertThat(house.lineNow()).isEqualTo("The plugs didn’t switch. Switch them on the Lights tab.")
    }

    /** Fails if turning off puts anything back in another order, or with other than what was saved, or leaves the record. */
    @Test
    fun turnOffPutsBackInOrder() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        house.turnOn(PARTY, by())
        val before = rig.calls.size

        house.turnOff(by())

        assertThat(rig.calls.drop(before)).containsExactly("music.stop", "lights.modeBack", "plugs.leave party").inOrder()
        assertThat(rig.music.stops).containsExactly(MADE to GROUPS_BEFORE)
        assertThat(rig.lights.backs).containsExactly(LIGHTS_BEFORE)
        assertThat(rig.saved()).isEqualTo(ModeRecord())
        assertThat(house.isOn.value).isFalse()
        assertThat(house.lineNow()).isNull()
    }

    /** Fails if the off lines aren't music, plugs, lights, shown for ten minutes, or a turn-on doesn't clear them. */
    @Test
    fun turnOffLinesAreMusicPlugsLights() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        val expected = "$MUSIC_STOP_LINE\n$LAMP_BACK_LINE\nSome lights didn’t go back: Kitchen."
        house.turnOn(PARTY, by())
        rig.music.stopped = HouseStep.Failed(MUSIC_STOP_LINE)
        rig.plugs.leaves += HouseStep.Failed(LAMP_BACK_LINE)
        rig.lights.notBack = listOf("Kitchen")

        house.turnOff(by())
        val off = rig.wall

        assertThat(house.lineNow()).isEqualTo(expected)
        rig.wall = off + HouseTimings.OFF_LINE_MS - 60_000
        house.tick()
        assertThat(house.lineNow()).isEqualTo(expected)
        rig.wall = off + HouseTimings.OFF_LINE_MS
        house.tick()
        assertThat(house.lineNow()).isNull()

        house.turnOn(PARTY, by())
        rig.plugs.leaves += HouseStep.Failed(LAMP_BACK_LINE)
        house.turnOff(by())
        assertThat(house.lineNow()).isNotNull()
        house.turnOn(PARTY, by())
        assertThat(house.lineNow()).isNull()
    }

    /** Fails if a throwing step stops Party ending, or the lines for them aren't the specified ones. */
    @Test
    fun turnOffWithEverythingThrowingStillEnds() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        house.turnOn(PARTY, by())
        rig.trace.throws += setOf("music.stop", "lights.modeBack", "plugs.leave")

        house.turnOff(by())

        assertThat(house.isOn.value).isFalse()
        assertThat(rig.saved()).isEqualTo(ModeRecord())
        assertThat(house.lineNow()).isEqualTo(
            "$MUSIC_STOP_LINE\nSome plugs may not have gone back. Check them on the Lights tab.\nSome lights didn’t go back: Kitchen and Lounge.",
        )
    }

    /** Fails if a music or lights seam that is gone, with something recorded to put back, leaves no line. */
    @Test
    fun seamsGoneAtTurnOffAreSaidSo() = runTest {
        val rig = rig()
        rig.choose()
        rig.house().turnOn(PARTY, by())

        val house = rig.restarted(music = null, lights = null)
        house.turnOff(by())

        assertThat(house.lineNow()).isEqualTo("$MUSIC_STOP_LINE\nSome lights didn’t go back: Kitchen and Lounge.")
        assertThat(house.isOn.value).isFalse()
    }

    /** Fails if a party that made no group has its music stopped, or says the music didn't stop. */
    @Test
    fun noGroupMadeNoMusicStop() = runTest {
        val rig = rig()
        rig.choose()
        rig.music.started = MusicStart(null, HouseStep.Done)
        val house = rig.house()
        house.turnOn(PARTY, by())

        house.turnOff(by())

        assertThat(rig.calls).doesNotContain("music.stop")
        assertThat(house.lineNow()).isNull()
    }

    /** Review Focus 3: fails if a restart mid-party forgets what to put back. */
    @Test
    fun aRestartMidPartyStillPutsBack() = runTest {
        val rig = rig()
        rig.choose()
        rig.house().turnOn(PARTY, by())
        val house = rig.restarted()

        house.turnOff(by())

        assertThat(rig.music.stops).containsExactly(MADE to GROUPS_BEFORE)
        assertThat(rig.lights.backs).containsExactly(LIGHTS_BEFORE)
        assertThat(rig.music.putBacks).isEmpty()
        assertThat(rig.calls.last()).isEqualTo("plugs.leave party")
        assertThat(rig.saved()).isEqualTo(ModeRecord())
    }

    /** Review Focus 3: fails if a restart that finds the snapshot but no group stops the music or shows a line. */
    @Test
    fun aRestartBeforeTheGroupWasMadeLeavesTheMusic() = runTest {
        val rig = rig()
        rig.settings.save(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1, musicBefore = GROUPS_BEFORE))
        val house = rig.restarted()
        house.tick()

        house.turnOff(by())

        assertThat(rig.music.stops).isEmpty()
        assertThat(rig.calls).doesNotContain("music.stop")
        assertThat(house.lineNow()).isNull()
    }

    /** Fails if the speakers' groups aren't put back once, with no pause, when the first load finds a snapshot and no group. */
    @Test
    fun aRestartThatFindsASnapshotButNoGroupPutsTheSpeakersBackOnce() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.settings.save(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1, musicBefore = GROUPS_BEFORE))
        val house = rig.restarted()

        house.tick()
        house.tick()

        assertThat(rig.music.putBacks).containsExactly(GROUPS_BEFORE)
        assertThat(rig.calls.filter { it.startsWith("music.") }).containsExactly("music.putBack")
        assertThat(rig.saved().musicBefore).isNull()
        assertThat(rig.saved().on).isTrue()
        assertThat(house.lineNow()).isNull()

        rig.restarted().tick()
        assertThat(rig.music.putBacks).hasSize(1)
    }

    /** Fails if a start that left no group keeps the snapshot, so a later restart regroups the speakers over hand changes. */
    @Test
    fun aStartThatLeftNoGroupDoesntKeepTheSnapshot() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.choose(PARTY_MODE.copy(music = PARTY_MIX))
        rig.music.started = MusicStart(null, HouseStep.Failed(MUSIC_START_LINE))
        rig.house().turnOn(PARTY, by())

        assertThat(rig.saved().musicBefore).isNull()
        assertThat(rig.saved().on).isTrue()
        rig.restarted().tick()

        assertThat(rig.music.putBacks).isEmpty()
    }

    /** Fails if a restart whose music system hasn't opened clears the snapshot, or the put-back isn't made once when it has. */
    @Test
    fun theSpeakersArePutBackOnceTheMusicSystemIsReady() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.settings.save(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1, musicBefore = GROUPS_BEFORE))
        rig.music.readyNow.value = false
        val house = rig.restarted()

        house.tick()
        assertThat(rig.music.putBacks).isEmpty()
        assertThat(rig.saved().musicBefore).isEqualTo(GROUPS_BEFORE)
        rig.music.readyNow.value = true
        house.tick()
        house.tick()

        assertThat(rig.music.putBacks).containsExactly(GROUPS_BEFORE)
        assertThat(rig.saved().musicBefore).isNull()
    }

    /** Fails if turning off before the music system opens drops the saved groups, so the speakers never go back. */
    @Test
    fun aTurnOffBeforeTheSpeakersOpenStillPutsTheSavedGroupsBack() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.settings.save(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1, musicBefore = GROUPS_BEFORE))
        rig.music.readyNow.value = false
        rig.restarted().turnOff(by())

        assertThat(rig.music.putBacks).isEmpty()
        assertThat(rig.saved().on).isFalse()
        rig.music.readyNow.value = true
        val house = rig.restarted()
        house.tick()
        house.tick()

        assertThat(rig.music.putBacks).containsExactly(GROUPS_BEFORE)
        assertThat(house.isOn.value).isFalse()
        assertThat(rig.saved()).isEqualTo(ModeRecord())
    }

    /** Fails if a put-back after Party is off fails without saying so. */
    @Test
    fun aPutBackAfterTurningOffThatFailsShowsItsLine() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.settings.save(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1, musicBefore = GROUPS_BEFORE))
        rig.music.readyNow.value = false
        val house = rig.restarted()
        house.turnOff(by())
        rig.music.putBackAnswer = HouseStep.Failed(SPEAKERS_NOT_BACK_LINE)
        rig.music.readyNow.value = true

        house.tick()

        assertThat(house.lineNow()).isEqualTo(SPEAKERS_NOT_BACK_LINE)
        assertThat(rig.saved()).isEqualTo(ModeRecord())
    }

    /** Fails if turning on puts the waiting groups back without saying when they didn't go back. */
    @Test
    fun aTurnOnWithGroupsWaitingPutsThemBackAndShowsARefusal() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.settings.save(ModeRecord(musicBefore = GROUPS_BEFORE))
        rig.music.putBackAnswer = HouseStep.Failed(SPEAKERS_NOT_BACK_LINE)
        val house = rig.restarted()

        house.turnOn(PARTY, by())

        assertThat(rig.music.putBacks).containsExactly(GROUPS_BEFORE)
        assertThat(house.lineNow()).isEqualTo("${ModesWords.NOTHING_TO_DO}\n$SPEAKERS_NOT_BACK_LINE")
    }

    /** Fails if a regroup that the speakers refuse isn't said on the tile. */
    @Test
    fun aRestartPutBackThatFailsShowsItsLine() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.settings.save(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1, musicBefore = GROUPS_BEFORE))
        rig.music.putBackAnswer = HouseStep.Failed(SPEAKERS_NOT_BACK_LINE)
        val house = rig.restarted()

        house.tick()

        assertThat(house.lineNow()).isEqualTo(SPEAKERS_NOT_BACK_LINE)
        assertThat(rig.saved().musicBefore).isNull()
    }

    /** Review Focus 3: fails if the plugs retry after a restart needs a grant or begins again. */
    @Test
    fun aPlugsRetryAfterARestartTakesNoGrant() = runTest {
        val rig = rig()
        rig.choose()
        rig.plugs.enters += HouseStep.Failed(LAMP_LINE)
        rig.house().turnOn(PARTY, by())
        val before = rig.calls.size
        val house = rig.restarted()

        house.tick()

        assertThat(rig.calls.drop(before)).containsExactly("plugs.enter party")
        assertThat(rig.plugs.begins).hasSize(1)
    }

    /** Review Focus 3: fails if a run left by a turn-on that never saved isn't put back once, with no line. */
    @Test
    fun anOrphanRunIsPutBackWhenPartyLoadsAsOff() = runTest {
        val rig = rig()
        rig.plugs.begun += HouseModes.PARTY
        val house = rig.house()

        house.tick()
        assertThat(rig.calls).containsExactly("plugs.leave party")
        assertThat(house.lineNow()).isNull()
        assertThat(rig.plugs.begun).isEmpty()
        house.tick()

        assertThat(rig.calls).containsExactly("plugs.leave party")
    }

    /** Fails if a record that won't save loses the party, or isn't saved at the next tick. */
    @Test
    fun aRecordThatWontSaveIsSavedAtTheNextTick() = runTest {
        val rig = rig()
        rig.store.failOnWrite = 1
        val house = rig.house()

        house.turnOn(PARTY, by())

        assertThat(house.isOn.value).isTrue()
        assertThat(rig.saved()).isEqualTo(ModeRecord())
        house.tick()

        assertThat(rig.saved().on).isTrue()
    }

    /** Fails if turning on while on calls anything again. */
    @Test
    fun turnOnWhileOnDoesNothing() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        house.turnOn(PARTY, by())
        val calls = rig.calls.toList()

        house.turnOn(PARTY, by())

        assertThat(rig.calls).containsExactlyElementsIn(calls).inOrder()
    }

    /** Fails if a turn-off meeting a turn-on waits for it or runs after it. */
    @Test
    fun aTurnOffMeetingATurnOnIsDropped() = runTest {
        val rig = rig()
        rig.choose()
        val gate = CompletableDeferred<Unit>()
        rig.trace.gates["plugs.begin"] = gate
        val house = rig.house()
        val turnOn = launch { house.turnOn(PARTY, by()) }
        runCurrent()
        assertThat(house.running.value).isEqualTo(ModeRunning(PARTY, PartyRun.TURNING_ON))

        house.turnOff(by())
        gate.complete(Unit)
        turnOn.join()

        assertThat(rig.calls).doesNotContain("music.stop")
        assertThat(house.isOn.value).isTrue()
        assertThat(house.running.value).isNull()
    }

    /** Fails if an empty list of speaker rooms reaches the music step. */
    @Test
    fun anEmptyRoomListIsNoSpeakerChosen() = runTest {
        val rig = rig()
        rig.choose(PARTY_MODE.copy(music = PARTY_MIX, speakers = emptyList()))
        val house = rig.house()

        house.turnOn(PARTY, by())

        assertThat(rig.calls.filter { it.startsWith("music.") }).isEmpty()
        assertThat(house.lineNow()).isEqualTo(MUSIC_START_LINE)
        assertThat(rig.saved().musicBefore).isNull()
    }

    /** Fails if the rooms chosen in Settings don't reach the music step. */
    @Test
    fun chosenRoomsAreWhatTheMusicPlaysOn() = runTest {
        val rig = rig()
        rig.choose(PARTY_MODE.copy(music = PARTY_MIX, speakers = listOf(KITCHEN, LOUNGE)))

        rig.house().turnOn(PARTY, by())

        assertThat(rig.music.startedWith.single().second).containsExactly("Kitchen-id", "Lounge-id")
    }

    /** Fails if isOn takes "off" for the answer before the store has spoken, or doesn't follow the record. */
    @Test
    fun isOnFollowsTheRecord() = runTest {
        val rig = Rig(backgroundScope, MemoryStore(null))
        val house = rig.house()
        val tick = launch { house.tick() }
        runCurrent()
        assertThat(house.isOn.value).isNull()
        assertThat(house.record.value).isNull()

        rig.store.state.value = MOVED
        tick.join()
        assertThat(house.isOn.value).isFalse()
        house.turnOn(PARTY, by())
        assertThat(house.isOn.value).isTrue()

        val restarted = rig.restarted()
        restarted.tick()
        assertThat(restarted.isOn.value).isTrue()
        restarted.turnOff(by())
        assertThat(restarted.isOn.value).isFalse()
    }

    /** 13 §2.14: fails if a log line holds a room, pick, plug or id, across turning on and off with everything failing. */
    @Test
    fun nothingPersonalReachesTheLog() = runTest {
        ShadowLog.clear()
        val rig = rig()
        rig.choose()
        rig.trace.throws += setOf("plugs.enter", "lights.modeBefore", "music.stop", "plugs.leave", "lights.modeBack")
        rig.music.started = MusicStart(null, HouseStep.Failed(MUSIC_START_LINE))
        val house = rig.house()
        house.turnOn(PARTY, by())
        house.turnOff(by())
        rig.trace.throws.clear()
        rig.settings.save(ModeRecord(modeId = HouseModes.PARTY, on = true, since = 1, musicBefore = GROUPS_BEFORE))
        rig.restarted().tick()

        val logs = ShadowLog.getLogs().filter { it.tag == "StoredModesHouse" }
        assertThat(logs).isNotEmpty()
        for (secret in listOf("Kitchen", "Lounge", "Party mix", "Lamp", "FV:", "-id")) {
            logs.forEach { assertThat(it.msg).doesNotContain(secret) }
        }
    }

    /** Review Focus 1, the house half: fails if Party on during the move doesn't load as on, or turning it off doesn't put back what 13 saved. */
    @Test
    fun aMovedOnRecordTurnsOffAndPutsBack() = runTest {
        val sep = "\u001F"
        val old = mutablePreferencesOf(
            booleanPreferencesKey("on") to true,
            longPreferencesKey("since") to 1_000L,
            stringPreferencesKey("music.made") to "Lounge-id${sep}Kitchen-id,Lounge-id,Dining-id",
            stringSetPreferencesKey("music.before") to setOf("Kitchen-id${sep}Kitchen-id", "Lounge-id${sep}Lounge-id,Dining-id"),
            stringSetPreferencesKey("lights.before") to setOf("r1${sep}Kitchen${sep}s1${sep}true${sep}40${sep}s2", "r2${sep}Lounge${sep}s3${sep}false${sep}0$sep"),
            stringSetPreferencesKey("scenes") to setOf("r1${sep}Kitchen${sep}s1${sep}Bright"),
        )
        val rig = Rig(backgroundScope, MemoryStore(PartyMove.migrate(old)))
        val house = rig.house()
        house.tick()
        assertThat(house.record.value).isEqualTo(FULL_RECORD)
        rig.calls.clear()

        house.turnOff(by())

        assertThat(rig.calls).containsExactly("music.stop", "lights.modeBack", "plugs.leave party").inOrder()
        assertThat(rig.music.stops).containsExactly(MADE to GROUPS_BEFORE)
        assertThat(rig.lights.backs).containsExactly(LIGHTS_BEFORE)
        assertThat(rig.saved()).isEqualTo(ModeRecord())
        assertThat(house.isOn.value).isFalse()
    }

    /** 15b decision 1: fails if a second stored mode turns on over the first, or touches anything. */
    @Test
    fun aSecondModeDoesntTurnOnOverTheFirst() = runTest {
        val rig = rig()
        rig.choose(FULL_MODE, FILM_NIGHT)
        val house = rig.house()
        house.turnOn(PARTY, by())
        val calls = rig.calls.toList()

        house.turnOn(FILM_ID, by())

        assertThat(house.onMode.value).isEqualTo(PARTY)
        assertThat(rig.calls).containsExactlyElementsIn(calls).inOrder()
    }

    /** 15b decision 7: fails if the wall turns on a mode that needs a PIN, or anything is called or saved first. */
    @Test
    fun theWallIsRefusedForAPinMode() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()

        val failure = runCatching { house.turnOn(PARTY, ModeGrant.Wall(PARTY)) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(rig.calls).isEmpty()
        assertThat(rig.saved()).isEqualTo(ModeRecord())
        assertThat(house.onMode.value).isNull()
    }

    /** 15b §4.2: fails if an On at a brightness or an Off room isn't asked for, set, saved and put back as a scene room is. */
    @Test
    fun percentAndOffRoomsRunAndGoBack() = runTest {
        val rig = rig()
        val bright = RoomChoice("r1", "Kitchen", LightChoice.On(60))
        val dark = RoomChoice("r2", "Lounge", LightChoice.Off)
        val before = listOf(
            RoomBefore("r1", "Kitchen", LightChoice.On(60), false, 0, null),
            RoomBefore("r2", "Lounge", LightChoice.Off, true, 80, "s3"),
        )
        rig.lights.snapshot = before
        rig.choose(PARTY_MODE.copy(lights = listOf(bright, dark)))
        val house = rig.house()

        house.turnOn(PARTY, by())

        assertThat(rig.lights.asked).containsExactly(listOf(bright, dark))
        assertThat(rig.calls).contains("lights.modeOn")
        assertThat(rig.saved().lights).isEqualTo(before)
        assertThat(house.lineNow()).isNull()
        house.turnOff(by())

        assertThat(rig.lights.backs).containsExactly(before)
    }

    /** 15b §4.3: fails if a room added while the mode is on is put back, or touched at all, by this run. */
    @Test
    fun anEditWhileOnChangesOnlyTheNextRun() = runTest {
        val rig = rig()
        val loungeBefore = LIGHTS_BEFORE[1]
        rig.lights.snapshot = listOf(loungeBefore)
        rig.choose(PARTY_MODE.copy(lights = listOf(LOUNGE_RELAX)))
        val house = rig.house()
        house.turnOn(PARTY, by())

        rig.settings.update { prefs -> prefs.withMode(PARTY) { it.withLight(KITCHEN_BRIGHT) } }
        house.turnOff(by())

        assertThat(rig.lights.asked).containsExactly(listOf(LOUNGE_RELAX))
        assertThat(rig.lights.backs).containsExactly(listOf(loungeBefore))
    }

    /** Ruling 8: fails if Holiday mode's refusal names the on mode by its old name after a rename. */
    @Test
    fun aRenameWhileOnShowsInTheRefusal() = runTest {
        val rig = rig()
        rig.choose(FILM_NIGHT)
        val house = rig.house()
        house.turnOn(FILM_ID, ModeGrant.Wall(FILM_ID))
        val holiday = TestSwitch(HouseModes.HOLIDAY, "Holiday mode", MutableStateFlow(false))
        val refusal = ModeRefusal(holiday, setOf(holiday, house))
        runCurrent()

        assertThat(refusal.refusesTap(turningOn = true)).isTrue()
        assertThat(refusal.current()).isEqualTo("Turn Film night off first.")
        rig.settings.update { prefs -> prefs.withMode(FILM_ID) { it.copy(name = "Movie night") } }
        runCurrent()

        assertThat(refusal.current()).isEqualTo("Turn Movie night off first.")
        assertThat(house.modeName).isEqualTo("Movie night")
    }

    /** 15b §5: fails if any log line, across every step failing, holds a mode's name or its id, or a no-PIN turn-on isn't said. */
    @Test
    fun logsNeverNameAMode() = runTest {
        ShadowLog.clear()
        val rig = rig()
        rig.choose(FILM_NIGHT.copy(speakers = emptyList()))
        rig.trace.throws += setOf("plugs.begin", "lights.modeBefore", "music.stop", "plugs.leave", "lights.modeBack")
        val house = rig.house()
        rig.store.failOnWrite = 1
        house.turnOn(FILM_ID, ModeGrant.Wall(FILM_ID))
        rig.choose(FILM_NIGHT.copy(needsPin = true))
        house.turnOff(ModeGrant.Wall(FILM_ID))
        house.turnOff(by())
        rig.trace.throws.clear()
        rig.choose(FILM_NIGHT)
        rig.trace.throws += "plugs.enter"
        house.turnOn(FILM_ID, ModeGrant.Wall(FILM_ID))
        house.turnOff(by())
        rig.settings.save(ModeRecord(modeId = FILM_ID, on = true, since = 1, musicBefore = GROUPS_BEFORE))
        rig.restarted().tick()

        val logs = ShadowLog.getLogs()
        assertThat(logs.map { it.msg }).contains("A stored mode turned on with no PIN")
        for (secret in listOf("Film night", FILM_ID)) {
            logs.forEach { assertThat(it.msg).doesNotContain(secret) }
        }
    }

    /** T1: fails if a restart with an added mode on doesn't load it as on, or turning it off doesn't put back its own parts. */
    @Test
    fun aRestartWithFilmNightOnPutsFilmNightBack() = runTest {
        val rig = rig()
        rig.choose(FULL_MODE, FILM_NIGHT)
        rig.settings.save(FULL_RECORD.copy(modeId = FILM_ID))
        val house = rig.restarted()
        house.tick()
        assertThat(house.onMode.value).isEqualTo(FILM_ID)
        rig.calls.clear()

        house.turnOff(by())

        assertThat(rig.calls).containsExactly("music.stop", "lights.modeBack", "plugs.leave $FILM_ID").inOrder()
        assertThat(rig.music.stops).containsExactly(MADE to GROUPS_BEFORE)
        assertThat(rig.lights.backs).containsExactly(LIGHTS_BEFORE)
        assertThat(house.onMode.value).isNull()
        assertThat(house.isOn.value).isFalse()
    }

    /** S3: fails if the wall's grant for one mode turns off another, or anything is put back. */
    @Test
    fun aWallCantTurnOffAnotherMode() = runTest {
        val rig = rig()
        rig.choose(FILM_NIGHT)
        val house = rig.house()
        house.turnOn(FILM_ID, ModeGrant.Wall(FILM_ID))
        val calls = rig.calls.toList()

        val failure = runCatching { house.turnOff(ModeGrant.Wall("modeother")) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(rig.calls).containsExactlyElementsIn(calls).inOrder()
        assertThat(house.onMode.value).isEqualTo(FILM_ID)
        assertThat(house.running.value).isNull()
    }

    /** T4: fails if a plug locked between the tap and the turn-on throws, or the turn-on doesn't run with its line shown. */
    @Test
    fun aPlugLockedBetweenTapAndTurnOnDoesntCrash() = runTest {
        val rig = rig()
        rig.choose(PARTY_MODE.copy(needsPin = false))
        rig.plugs.addedNow.value = listOf(LOCKED_HEATER)
        rig.plugs.enters += HouseStep.Failed(LEFT_OUT)
        val house = rig.house()

        house.turnOn(PARTY, ModeGrant.Wall(PARTY))

        assertThat(house.onMode.value).isEqualTo(PARTY)
        assertThat(rig.plugs.grants.single()).isInstanceOf(ModeGrant.Wall::class.java)
        assertThat(house.lineNow()).isEqualTo(LEFT_OUT)
    }

    /** S1: fails if the wall turns off a mode that has come to need a PIN, or the tile doesn't say to use one. */
    @Test
    fun aWallTurnOffOfAModeThatNowNeedsAPinLeavesItOn() = runTest {
        val rig = rig()
        rig.choose(FILM_NIGHT)
        val house = rig.house()
        house.turnOn(FILM_ID, ModeGrant.Wall(FILM_ID))
        val calls = rig.calls.toList()
        rig.plugs.holdsPin = true

        house.turnOff(ModeGrant.Wall(FILM_ID))

        assertThat(rig.calls).containsExactlyElementsIn(calls).inOrder()
        assertThat(house.onMode.value).isEqualTo(FILM_ID)
        assertThat(house.lineNow(FILM_ID)).isEqualTo("Turn Film night off with a PIN.")
        house.turnOff(by())
        assertThat(house.onMode.value).isNull()
        assertThat(house.lineNow(FILM_ID)).isNull()
    }

    /** Ruling 14: fails if the PIN rule misses the mode's flag, an added locked plug, a locked plug the run holds, or an unreadable store. */
    @Test
    fun needsPinFollowsRuling14() = runTest {
        val rig = rig()
        rig.choose(PARTY_MODE, FILM_NIGHT)
        val house = rig.house()

        assertThat(house.needsPin(PARTY)).isTrue()
        assertThat(house.needsPin(FILM_ID)).isFalse()
        rig.plugs.addedNow.value = listOf(LOCKED_HEATER)
        assertThat(house.needsPin(FILM_ID)).isTrue()
        rig.plugs.addedNow.value = emptyList()
        rig.plugs.holdsPin = true
        assertThat(house.needsPin(FILM_ID)).isTrue()
        rig.plugs.holdsPin = false
        rig.plugs.addedThrows = true
        assertThat(house.needsPin(FILM_ID)).isTrue()
        assertThat(house.needsPin("modeother")).isTrue()
    }

    /** Fails if the running change can be read out of a log line. */
    @Test
    fun theRunHidesItsMode() {
        assertThat(ModeRunning(FILM_ID, PartyRun.TURNING_ON).toString()).doesNotContain(FILM_ID)
    }

    private companion object {
        const val PARTY = HouseModes.PARTY
        const val LEFT_OUT = "Heater needs a PIN, so it wasn’t switched."
        const val MUSIC_START_LINE = "The music didn’t start. Check the Sonos app."
        const val MUSIC_STOP_LINE = "The music didn’t stop. Stop it in the Sonos app."
        const val SPEAKERS_NOT_BACK_LINE = "The speakers didn’t go back to their groups. Regroup them in the Sonos app."
        const val LAMP_LINE = "Lamp didn’t switch off. It’ll keep trying."
        const val LAMP_BACK_LINE = "Lamp didn’t switch back on. Switch it on the Lights tab."
    }
}
