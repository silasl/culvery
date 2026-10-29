package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.capability.music.RoomGroup
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.DINING
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.HOUSEHOLD
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.KITCHEN
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.LOUNGE

private const val GONE = "RINCON_000E58EEEEEE01400"

/**
 * 13 §4.1, §4.2: three speakers on localhost. Kitchen answers the layout, which each test sets as it is before the
 * command and as it is after (the read after follows the last command).
 */
@RunWith(AndroidJUnit4::class)
class SonosGroupingTest {
    private val kitchen = FakeSpeaker()
    private val lounge = FakeSpeaker()
    private val dining = FakeSpeaker()
    private val client = SonosClient(testHttp(), onThisMachine) { _, _ -> }
    private val system = SonosSystem(client, { emptyList() }, { true }, MusicLink(HOUSEHOLD, kitchen.address), {}, WallClock { 1_000_000L })

    @Before
    fun setUp() {
        listOf(kitchen, lounge, dining).forEach {
            it.answer("GetHouseholdID", SonosFixtures.household())
            it.acceptCommands()
            it.answer("GetVolume", SonosFixtures.volume(30))
        }
    }

    @After
    fun tearDown() {
        listOf(kitchen, lounge, dining).forEach { it.close() }
    }

    private fun apart() = SonosFixtures.apart(kitchen.address, lounge.address, dining.address)
    private fun underKitchen() = SonosFixtures.groupedUnderKitchen(kitchen.address, lounge.address, dining.address)
    private fun kitchenAndLounge() = SonosFixtures.kitchenAndLounge(kitchen.address, lounge.address, dining.address)
    private fun loungeLeadsDining() = SonosFixtures.loungeLeadsDining(kitchen.address, lounge.address, dining.address)

    /** The layout the first read finds, then the one every read after finds. */
    private fun layouts(before: String, after: String) = kitchen.answerInOrder("GetZoneGroupState", before, after)

    private fun FakeSpeaker.joins() = received.filter { it.action == "SetAVTransportURI" }
    private fun FakeSpeaker.leaves() = received.filter { it.action == "BecomeCoordinatorOfStandaloneGroup" }
    private fun FakeSpeaker.commands() = joins() + leaves()

    @Test
    fun groupJoinsEachChosenRoomToTheCoordinator() = runTest {
        layouts(apart(), underKitchen())
        val made = system.group(KITCHEN, setOf(KITCHEN, LOUNGE, DINING))
        assertThat(lounge.joins()).hasSize(1)
        assertThat(lounge.joins().single().body).contains("<CurrentURI>x-rincon:$KITCHEN</CurrentURI>")
        assertThat(dining.joins()).hasSize(1)
        assertThat(dining.joins().single().body).contains("<CurrentURI>x-rincon:$KITCHEN</CurrentURI>")
        assertThat(kitchen.commands()).isEmpty()
        val lastJoin = maxOf(lounge.joins().single().order, dining.joins().single().order)
        assertThat(kitchen.received.last { it.action == "GetZoneGroupState" }.order).isGreaterThan(lastJoin)
        assertThat(made.coordinatorId).isEqualTo(KITCHEN)
        assertThat(made.rooms.map { it.id }).containsExactly(KITCHEN, LOUNGE, DINING)
    }

    /** Sonos shows an acknowledged join in the topology a moment later: the read straight after the joins may still lag. */
    @Test
    fun aLayoutThatLagsBehindTheJoinsIsReadAgain() = runTest {
        kitchen.answerInOrder("GetZoneGroupState", apart(), apart(), kitchenAndLounge(), underKitchen())
        val made = system.group(KITCHEN, setOf(KITCHEN, LOUNGE, DINING))
        assertThat(made.rooms.map { it.id }).containsExactly(KITCHEN, LOUNGE, DINING)
        assertThat(lounge.joins()).hasSize(1)
        assertThat(dining.joins()).hasSize(1)
    }

    @Test
    fun aRegroupWhoseLayoutLagsIsReadAgainAndSendsNothingTwice() = runTest {
        kitchen.answerInOrder("GetZoneGroupState", underKitchen(), underKitchen(), loungeLeadsDining())
        system.regroup(listOf(RoomGroup(KITCHEN, listOf(KITCHEN)), RoomGroup(LOUNGE, listOf(LOUNGE, DINING))))
        assertThat(kitchen.received.count { it.action == "GetZoneGroupState" }).isEqualTo(3)
        assertThat(lounge.leaves()).hasSize(1)
        assertThat(dining.joins()).hasSize(1)
    }

    @Test
    fun aLayoutThatNeverShowsTheJoinsReturnsWhatTheLastReadShows() = runTest {
        kitchen.answerInOrder("GetZoneGroupState", apart())
        val made = system.group(KITCHEN, setOf(KITCHEN, LOUNGE))
        assertThat(made.rooms.map { it.id }).containsExactly(KITCHEN)
        assertThat(kitchen.received.count { it.action == "GetZoneGroupState" }).isEqualTo(1 + SETTLE_READS)
    }

    @Test
    fun aRoomAlreadyInTheGroupIsNotSentAJoin() = runTest {
        layouts(kitchenAndLounge(), underKitchen())
        system.group(KITCHEN, setOf(KITCHEN, LOUNGE, DINING))
        assertThat(dining.joins()).hasSize(1)
        assertThat(lounge.commands()).isEmpty()
        assertThat(kitchen.commands()).isEmpty()
    }

    @Test
    fun aRoomNotChosenLeavesTheCoordinatorsGroup() = runTest {
        layouts(underKitchen(), kitchenAndLounge())
        val made = system.group(KITCHEN, setOf(KITCHEN, LOUNGE))
        assertThat(dining.leaves()).hasSize(1)
        assertThat(listOf(kitchen, lounge, dining).flatMap { it.joins() }).isEmpty()
        assertThat(made.rooms.map { it.id }).containsExactly(KITCHEN, LOUNGE)
    }

    @Test
    fun aCoordinatorInAnotherGroupStandsAloneFirst() = runTest {
        layouts(underKitchen(), loungeLeadsDining())
        system.group(LOUNGE, setOf(LOUNGE, DINING))
        assertThat(lounge.leaves()).hasSize(1)
        assertThat(dining.joins().single().body).contains("<CurrentURI>x-rincon:$LOUNGE</CurrentURI>")
        assertThat(lounge.leaves().single().order).isLessThan(dining.joins().single().order)
        assertThat(kitchen.commands()).isEmpty()
    }

    /** Review Focus 2: the rooms that joined still play, so a refusal is the caller's to compare, not an exception. */
    @Test
    fun aRoomThatRefusesIsLeftOutNotThrown() = runTest {
        dining.refuse("SetAVTransportURI", 701)
        layouts(apart(), kitchenAndLounge())
        val made = system.group(KITCHEN, setOf(KITCHEN, LOUNGE, DINING))
        assertThat(dining.joins()).hasSize(1)
        assertThat(made.rooms.map { it.id }).containsExactly(KITCHEN, LOUNGE)
    }

    @Test
    fun theCoordinatorUnreachableThrows() = runTest {
        layouts(apart(), underKitchen())
        kitchen.silent = true
        val problem = throws<MusicUnreachableException> { system.group(KITCHEN, setOf(KITCHEN, LOUNGE)) }.problem
        assertThat(problem).isEqualTo(MusicProblem.UNREACHABLE)
        assertThat(lounge.commands()).isEmpty()
    }

    @Test
    fun regroupPutsEachRoomBackUnderItsCoordinator() = runTest {
        layouts(underKitchen(), loungeLeadsDining())
        system.regroup(listOf(RoomGroup(KITCHEN, listOf(KITCHEN)), RoomGroup(LOUNGE, listOf(LOUNGE, DINING))))
        assertThat(lounge.leaves()).hasSize(1)
        assertThat(dining.joins().single().body).contains("<CurrentURI>x-rincon:$LOUNGE</CurrentURI>")
        assertThat(lounge.leaves().single().order).isLessThan(dining.joins().single().order)
        assertThat(kitchen.commands()).isEmpty()
    }

    @Test
    fun aGroupAlreadyAsRememberedIsLeft() = runTest {
        layouts(loungeLeadsDining(), loungeLeadsDining())
        system.regroup(listOf(RoomGroup(KITCHEN, listOf(KITCHEN)), RoomGroup(LOUNGE, listOf(LOUNGE, DINING))))
        assertThat(listOf(kitchen, lounge, dining).flatMap { it.commands() }).isEmpty()
    }

    @Test
    fun aRoomGoneFromTheHouseholdIsSkipped() = runTest {
        layouts(apart(), kitchenAndLounge())
        system.regroup(listOf(RoomGroup(KITCHEN, listOf(KITCHEN, LOUNGE, GONE)), RoomGroup(GONE, listOf(GONE, DINING))))
        assertThat(lounge.joins()).hasSize(1)
        assertThat(listOf(kitchen, dining).flatMap { it.commands() }).isEmpty()
        assertThat(lounge.joins().single().body).doesNotContain(GONE)
    }

    @Test
    fun aRefusedRoomInRegroupThrowsAfterTryingTheRest() = runTest {
        lounge.refuse("BecomeCoordinatorOfStandaloneGroup", 701)
        layouts(underKitchen(), loungeLeadsDining())
        val refused = throws<MusicRefusedException> {
            system.regroup(listOf(RoomGroup(KITCHEN, listOf(KITCHEN)), RoomGroup(LOUNGE, listOf(LOUNGE, DINING))))
        }
        assertThat(refused.code).isEqualTo(701)
        assertThat(dining.joins()).hasSize(1)
    }

    @Test
    fun anotherHouseholdIsNeverGrouped() = runTest {
        layouts(apart(), underKitchen())
        kitchen.answer("GetHouseholdID", SonosFixtures.household("Sonos_someoneElse"))
        system.checkLayout()
        listOf(kitchen, lounge, dining).forEach { it.received.clear() }
        assertThat(throws<MusicUnreachableException> { system.group(KITCHEN, setOf(KITCHEN, LOUNGE)) }.problem).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(throws<MusicUnreachableException> { system.regroup(listOf(RoomGroup(KITCHEN, listOf(KITCHEN, LOUNGE)))) }.problem)
            .isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(listOf(kitchen, lounge, dining).flatMap { it.actions() }).isEmpty()
    }
}
