package uk.co.siland.culvery.capability.party

import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.MusicStart
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.plugin.WallClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PartyHouseTest {
    /** The settings over one store, the three fake seams sharing one call list, and the wall millis the test sets. */
    private class Rig(private val scope: CoroutineScope, val store: MemoryStore = MemoryStore()) {
        val trace = Trace()
        val settings = PartySettings(store, scope)
        val plugs = FakePlugs(trace)
        val music = FakeMusic(trace)
        val lights = FakeLights(trace)
        var wall = 1_000_000L
        val calls get() = trace.calls

        fun house(
            plugs: HousePlugs? = this.plugs,
            music: HouseMusic? = this.music,
            lights: HouseLights? = this.lights,
            settings: PartySettings = this.settings,
        ) = PartyHouse(settings, plugs, music, lights, WallClock { wall })

        /** The process starting again: a new store reader over the same file, and a new house. */
        fun restarted(
            plugs: HousePlugs? = this.plugs,
            music: HouseMusic? = this.music,
            lights: HouseLights? = this.lights,
        ) = house(plugs, music, lights, PartySettings(store, scope))

        /** Party mix on every speaker, Bright in Kitchen and Relax in Lounge. */
        suspend fun choose(prefs: PartyPrefs = PartyPrefs(music = PARTY_MIX, scenes = listOf(KITCHEN_BRIGHT, LOUNGE_RELAX))) =
            settings.update { prefs }

        /** What the file holds now, read by a fresh reader. */
        suspend fun saved(): PartyRecord = PartySettings(store, scope).record.filterNotNull().first()

        fun enters() = calls.count { it == "plugs.enter party" }
    }

    private fun TestScope.rig() = Rig(backgroundScope)

    private fun by(): Authorised = grant(HousePermissions.PARTY_MODE)

    /** Fails if another mode's grant turns Party on, or anything is called or saved before the refusal. */
    @Test
    fun turnOnNeedsItsOwnGrant() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()

        val failure = runCatching { house.turnOn(grant(HousePermissions.HOLIDAY_MODE)) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(rig.calls).isEmpty()
        assertThat(rig.saved()).isEqualTo(PartyRecord())
    }

    /** Fails if the steps run in another order, the begin lacks the grant, or the record lacks a snapshot. */
    @Test
    fun turnOnRunsEachStepInOrder() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        val by = by()

        house.turnOn(by)

        assertThat(rig.calls).containsExactly(
            "plugs.begin party", "plugs.enter party", "music.before", "music.start FV:2/3 null", "lights.partyBefore", "lights.partyOn",
        ).inOrder()
        assertThat(rig.plugs.begins).containsExactly(by)
        val expected = PartyRecord(on = true, since = rig.wall, musicBefore = GROUPS_BEFORE, made = MADE, lights = LIGHTS_BEFORE)
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

        val turnOn = launch { house.turnOn(by()) }
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
        val musicTurnOn = launch { musicHouse.turnOn(by()) }
        runCurrent()

        assertThat(music.calls.last()).isEqualTo("music.start FV:2/3 null")
        assertThat(music.saved().musicBefore).isEqualTo(GROUPS_BEFORE)
        assertThat(music.saved().made).isNull()
        musicGate.complete(Unit)
        musicTurnOn.join()

        val lights = rig()
        lights.choose()
        val lightsGate = CompletableDeferred<Unit>()
        lights.trace.gates["lights.partyOn"] = lightsGate
        val lightsHouse = lights.house()
        val lightsTurnOn = launch { lightsHouse.turnOn(by()) }
        runCurrent()

        assertThat(lights.calls.last()).isEqualTo("lights.partyOn")
        assertThat(lights.saved().lights).isEqualTo(LIGHTS_BEFORE)
        assertThat(lights.saved().made).isEqualTo(MADE)
        lightsGate.complete(Unit)
        lightsTurnOn.join()
    }

    /** Fails if Party with nothing chosen and no plugs listed refuses, calls a seam, or shows a line. */
    @Test
    fun nothingConfiguredStillTurnsOn() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        val house = rig.house()

        house.turnOn(by())

        assertThat(rig.saved().on).isTrue()
        assertThat(rig.calls).isEmpty()
        assertThat(house.line.value).isNull()
        assertThat(house.plan().nothing).isTrue()
    }

    /** Fails if a music system that isn't ready is called or gets a line, though a pick is saved. */
    @Test
    fun aSystemNotReadyIsNoStep() = runTest {
        val rig = rig()
        rig.choose()
        rig.music.readyNow.value = false
        val house = rig.house()

        house.turnOn(by())

        assertThat(rig.calls.filter { it.startsWith("music.") }).isEmpty()
        assertThat(house.line.value).isNull()
        assertThat(rig.saved().musicBefore).isNull()
    }

    /** Review Focus 2: fails if one step failing or throwing stops another, or the lines aren't the failed ones. */
    @Test
    fun aFailingStepDoesntStopTheOthers() = runTest {
        val rig = rig()
        rig.choose()
        rig.music.started = MusicStart(null, HouseStep.Failed(MUSIC_START_LINE))
        rig.trace.throws += "lights.partyBefore"
        val house = rig.house()

        house.turnOn(by())

        assertThat(rig.calls).containsExactly(
            "plugs.begin party", "plugs.enter party", "music.before", "music.start FV:2/3 null", "lights.partyBefore",
        ).inOrder()
        assertThat(house.isOn.value).isTrue()
        assertThat(house.line.value).isEqualTo("$MUSIC_START_LINE\nSome lights didn’t change: Kitchen and Lounge.")
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

        house.turnOn(by())

        assertThat(house.line.value).isEqualTo("$MUSIC_START_LINE\n$LAMP_LINE\nSome lights didn’t change: Kitchen.")
    }

    /** Fails if the plugs aren't retried at five minutes and no sooner, or music and lights are tried again. */
    @Test
    fun plugsAreRetriedEveryFiveMinutes() = runTest {
        val rig = rig()
        rig.choose()
        rig.plugs.enters += HouseStep.Failed(LAMP_LINE)
        val house = rig.house()
        house.turnOn(by())
        val turnedOn = rig.wall
        assertThat(house.line.value).isEqualTo(LAMP_LINE)

        rig.wall = turnedOn + PartyHouse.RETRY_MS - 60_000
        house.tick()
        assertThat(rig.enters()).isEqualTo(1)
        rig.wall = turnedOn + PartyHouse.RETRY_MS
        house.tick()

        assertThat(rig.enters()).isEqualTo(2)
        assertThat(house.line.value).isNull()
        assertThat(rig.calls.count { it.startsWith("music.start") }).isEqualTo(1)
        assertThat(rig.calls.count { it == "lights.partyOn" }).isEqualTo(1)
        rig.wall = turnedOn + 3 * PartyHouse.RETRY_MS
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
        house.turnOn(by())
        val turnedOn = rig.wall
        assertThat(house.line.value).isEqualTo("The plugs didn’t switch. They’ll keep trying.")

        rig.trace.throws -= "plugs.enter"
        rig.wall = turnedOn + PartyHouse.RETRY_MS - 60_000
        house.tick()
        assertThat(rig.enters()).isEqualTo(1)
        rig.wall = turnedOn + PartyHouse.RETRY_MS
        house.tick()

        assertThat(rig.enters()).isEqualTo(2)
        assertThat(house.line.value).isNull()
    }

    /** Fails if a begin that threw stops the turn-on, the missing run isn't missed with its own line, or it's tried again. */
    @Test
    fun aPlugsStepWithNoRunIsMissed() = runTest {
        val rig = rig()
        rig.trace.throws += "plugs.begin"
        val house = rig.house()
        house.turnOn(by())
        val turnedOn = rig.wall

        assertThat(rig.saved().on).isTrue()
        assertThat(house.line.value).isEqualTo("The plugs didn’t switch. Switch them on the Plugs tab.")
        rig.wall = turnedOn + 2 * PartyHouse.RETRY_MS
        house.tick()

        assertThat(rig.enters()).isEqualTo(1)
        assertThat(house.line.value).isEqualTo("The plugs didn’t switch. Switch them on the Plugs tab.")
    }

    /** Fails if turning off puts anything back in another order, or with other than what was saved, or leaves the record. */
    @Test
    fun turnOffPutsBackInOrder() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        house.turnOn(by())
        val before = rig.calls.size

        house.turnOff(by())

        assertThat(rig.calls.drop(before)).containsExactly("music.stop", "lights.partyBack", "plugs.leave party").inOrder()
        assertThat(rig.music.stops).containsExactly(MADE to GROUPS_BEFORE)
        assertThat(rig.lights.backs).containsExactly(LIGHTS_BEFORE)
        assertThat(rig.saved()).isEqualTo(PartyRecord())
        assertThat(house.isOn.value).isFalse()
        assertThat(house.line.value).isNull()
    }

    /** Fails if the off lines aren't music, plugs, lights, shown for ten minutes, or a turn-on doesn't clear them. */
    @Test
    fun turnOffLinesAreMusicPlugsLights() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        val expected = "$MUSIC_STOP_LINE\n$LAMP_BACK_LINE\nSome lights didn’t go back: Kitchen."
        house.turnOn(by())
        rig.music.stopped = HouseStep.Failed(MUSIC_STOP_LINE)
        rig.plugs.leaves += HouseStep.Failed(LAMP_BACK_LINE)
        rig.lights.notBack = listOf("Kitchen")

        house.turnOff(by())
        val off = rig.wall

        assertThat(house.line.value).isEqualTo(expected)
        rig.wall = off + PartyHouse.OFF_LINE_MS - 60_000
        house.tick()
        assertThat(house.line.value).isEqualTo(expected)
        rig.wall = off + PartyHouse.OFF_LINE_MS
        house.tick()
        assertThat(house.line.value).isNull()

        house.turnOn(by())
        rig.plugs.leaves += HouseStep.Failed(LAMP_BACK_LINE)
        house.turnOff(by())
        assertThat(house.line.value).isNotNull()
        house.turnOn(by())
        assertThat(house.line.value).isNull()
    }

    /** Fails if a throwing step stops Party ending, or the lines for them aren't the specified ones. */
    @Test
    fun turnOffWithEverythingThrowingStillEnds() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        house.turnOn(by())
        rig.trace.throws += setOf("music.stop", "lights.partyBack", "plugs.leave")

        house.turnOff(by())

        assertThat(house.isOn.value).isFalse()
        assertThat(rig.saved()).isEqualTo(PartyRecord())
        assertThat(house.line.value).isEqualTo(
            "$MUSIC_STOP_LINE\nSome plugs may not have gone back. Check them on the Plugs tab.\nSome lights didn’t go back: Kitchen and Lounge.",
        )
    }

    /** Fails if a music or lights seam that is gone, with something recorded to put back, leaves no line. */
    @Test
    fun seamsGoneAtTurnOffAreSaidSo() = runTest {
        val rig = rig()
        rig.choose()
        rig.house().turnOn(by())

        val house = rig.restarted(music = null, lights = null)
        house.turnOff(by())

        assertThat(house.line.value).isEqualTo("$MUSIC_STOP_LINE\nSome lights didn’t go back: Kitchen and Lounge.")
        assertThat(house.isOn.value).isFalse()
    }

    /** Fails if a party that made no group has its music stopped, or says the music didn't stop. */
    @Test
    fun noGroupMadeNoMusicStop() = runTest {
        val rig = rig()
        rig.choose()
        rig.music.started = MusicStart(null, HouseStep.Done)
        val house = rig.house()
        house.turnOn(by())

        house.turnOff(by())

        assertThat(rig.calls).doesNotContain("music.stop")
        assertThat(house.line.value).isNull()
    }

    /** Review Focus 3: fails if a restart mid-party forgets what to put back. */
    @Test
    fun aRestartMidPartyStillPutsBack() = runTest {
        val rig = rig()
        rig.choose()
        rig.house().turnOn(by())
        val house = rig.restarted()

        house.turnOff(by())

        assertThat(rig.music.stops).containsExactly(MADE to GROUPS_BEFORE)
        assertThat(rig.lights.backs).containsExactly(LIGHTS_BEFORE)
        assertThat(rig.music.putBacks).isEmpty()
        assertThat(rig.calls.last()).isEqualTo("plugs.leave party")
        assertThat(rig.saved()).isEqualTo(PartyRecord())
    }

    /** Review Focus 3: fails if a restart that finds the snapshot but no group stops the music or shows a line. */
    @Test
    fun aRestartBeforeTheGroupWasMadeLeavesTheMusic() = runTest {
        val rig = rig()
        rig.settings.save(PartyRecord(on = true, since = 1, musicBefore = GROUPS_BEFORE))
        val house = rig.restarted()
        house.tick()

        house.turnOff(by())

        assertThat(rig.music.stops).isEmpty()
        assertThat(rig.calls).doesNotContain("music.stop")
        assertThat(house.line.value).isNull()
    }

    /** Fails if the speakers' groups aren't put back once, with no pause, when the first load finds a snapshot and no group. */
    @Test
    fun aRestartThatFindsASnapshotButNoGroupPutsTheSpeakersBackOnce() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.settings.save(PartyRecord(on = true, since = 1, musicBefore = GROUPS_BEFORE))
        val house = rig.restarted()

        house.tick()
        house.tick()

        assertThat(rig.music.putBacks).containsExactly(GROUPS_BEFORE)
        assertThat(rig.calls.filter { it.startsWith("music.") }).containsExactly("music.putBack")
        assertThat(rig.saved().musicBefore).isNull()
        assertThat(rig.saved().on).isTrue()
        assertThat(house.line.value).isNull()

        rig.restarted().tick()
        assertThat(rig.music.putBacks).hasSize(1)
    }

    /** Fails if a start that left no group keeps the snapshot, so a later restart regroups the speakers over hand changes. */
    @Test
    fun aStartThatLeftNoGroupDoesntKeepTheSnapshot() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.choose(PartyPrefs(music = PARTY_MIX))
        rig.music.started = MusicStart(null, HouseStep.Failed(MUSIC_START_LINE))
        rig.house().turnOn(by())

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
        rig.settings.save(PartyRecord(on = true, since = 1, musicBefore = GROUPS_BEFORE))
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

    /** Fails if a regroup that the speakers refuse isn't said on the tile. */
    @Test
    fun aRestartPutBackThatFailsShowsItsLine() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = false
        rig.settings.save(PartyRecord(on = true, since = 1, musicBefore = GROUPS_BEFORE))
        rig.music.putBackAnswer = HouseStep.Failed(SPEAKERS_NOT_BACK_LINE)
        val house = rig.restarted()

        house.tick()

        assertThat(house.line.value).isEqualTo(SPEAKERS_NOT_BACK_LINE)
        assertThat(rig.saved().musicBefore).isNull()
    }

    /** Review Focus 3: fails if the plugs retry after a restart needs a grant or begins again. */
    @Test
    fun aPlugsRetryAfterARestartTakesNoGrant() = runTest {
        val rig = rig()
        rig.choose()
        rig.plugs.enters += HouseStep.Failed(LAMP_LINE)
        rig.house().turnOn(by())
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
        assertThat(house.line.value).isNull()
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

        house.turnOn(by())

        assertThat(house.isOn.value).isTrue()
        assertThat(rig.saved()).isEqualTo(PartyRecord())
        house.tick()

        assertThat(rig.saved().on).isTrue()
    }

    /** Fails if turning on while on calls anything again. */
    @Test
    fun turnOnWhileOnDoesNothing() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()
        house.turnOn(by())
        val calls = rig.calls.toList()

        house.turnOn(by())

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
        val turnOn = launch { house.turnOn(by()) }
        runCurrent()
        assertThat(house.running.value).isEqualTo(PartyRun.TURNING_ON)

        house.turnOff(by())
        gate.complete(Unit)
        turnOn.join()

        assertThat(rig.calls).doesNotContain("music.stop")
        assertThat(house.isOn.value).isTrue()
        assertThat(house.running.value).isNull()
    }

    /** Fails if the plan isn't the plugs' preview and the saved choices, or lists a part whose system isn't ready. */
    @Test
    fun planListsWhatWillHappen() = runTest {
        val rig = rig()
        rig.choose()
        val house = rig.house()

        val plan = house.plan()

        assertThat(plan.plugs).containsExactly(PlugChange("Lamp", false))
        assertThat(plan.music).isEqualTo(PARTY_MIX)
        assertThat(plan.rooms).isNull()
        assertThat(plan.scenes).containsExactly(KITCHEN_BRIGHT, LOUNGE_RELAX)

        rig.music.readyNow.value = false
        rig.lights.readyNow.value = false
        rig.plugs.readyNow.value = false
        val bare = house.plan()
        assertThat(bare.music).isNull()
        assertThat(bare.scenes).isEmpty()
        assertThat(bare.plugs).isEmpty()
        assertThat(bare.nothing).isTrue()
    }

    /** Fails if an empty list of speaker rooms reaches the music step or the sheet, which would read "on ." */
    @Test
    fun anEmptyRoomListIsNoSpeakerChosen() = runTest {
        val rig = rig()
        rig.choose(PartyPrefs(music = PARTY_MIX, rooms = emptyList()))
        val house = rig.house()

        assertThat(house.plan().music).isNull()
        house.turnOn(by())

        assertThat(rig.calls.filter { it.startsWith("music.") }).isEmpty()
        assertThat(house.line.value).isEqualTo(MUSIC_START_LINE)
        assertThat(rig.saved().musicBefore).isNull()
    }

    /** Fails if the rooms chosen in Settings don't reach the music step. */
    @Test
    fun chosenRoomsAreWhatTheMusicPlaysOn() = runTest {
        val rig = rig()
        rig.choose(PartyPrefs(music = PARTY_MIX, rooms = listOf(KITCHEN, LOUNGE)))

        rig.house().turnOn(by())

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

        rig.store.state.value = emptyPreferences()
        tick.join()
        assertThat(house.isOn.value).isFalse()
        house.turnOn(by())
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
        rig.trace.throws += setOf("plugs.enter", "lights.partyBefore", "music.stop", "plugs.leave", "lights.partyBack")
        rig.music.started = MusicStart(null, HouseStep.Failed(MUSIC_START_LINE))
        val house = rig.house()
        house.turnOn(by())
        house.turnOff(by())
        rig.trace.throws.clear()
        rig.settings.save(PartyRecord(on = true, since = 1, musicBefore = GROUPS_BEFORE))
        rig.restarted().tick()

        val logs = ShadowLog.getLogs().filter { it.tag == "PartyHouse" }
        assertThat(logs).isNotEmpty()
        for (secret in listOf("Kitchen", "Lounge", "Party mix", "Lamp", "FV:", "-id")) {
            logs.forEach { assertThat(it.msg).doesNotContain(secret) }
        }
    }

    private companion object {
        const val MUSIC_START_LINE = "The music didn’t start. Check the Sonos app."
        const val MUSIC_STOP_LINE = "The music didn’t stop. Stop it in the Sonos app."
        const val SPEAKERS_NOT_BACK_LINE = "The speakers didn’t go back to their groups. Regroup them in the Sonos app."
        const val LAMP_LINE = "Lamp didn’t switch off. It’ll keep trying."
        const val LAMP_BACK_LINE = "Lamp didn’t switch back on. Switch it on the Plugs tab."
    }
}
