package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §2.2, with Home keeping a group it showed playing while that group is paused. */
class HomePickTest {
    private val kitchen = group("k", "Kitchen")
    private val lounge = group("l", "Lounge", "Dining room")
    private val bedroom = group("b", "Bedroom")
    private val all = listOf(kitchen, lounge, bedroom)

    private fun List<SpeakerGroup>.with(id: String, state: PlayState, startedAt: Long? = null) =
        map { if (it.id == id) it.copy(state = state, nowPlaying = SONG, startedAtMillis = startedAt) else it }

    @Test
    fun theOnlyPlayingGroup() {
        assertThat(homePick(all.with("b", PlayState.PLAYING), "Kitchen-id", null)).isEqualTo(HomePick(all.with("b", PlayState.PLAYING)[2], false))
    }

    @Test
    fun ofSeveralPlayingTheOneWithTheUsualRoom() {
        val groups = all.with("k", PlayState.PLAYING, 100).with("l", PlayState.PLAYING, 200)
        assertThat(homePick(groups, "Dining room-id", null)?.group?.id).isEqualTo("l")
        assertThat(homePick(groups, "Kitchen-id", null)?.group?.id).isEqualTo("k")
    }

    /** Fails if the earlier start wins, or Sonos's order does. */
    @Test
    fun ofSeveralPlayingWithoutTheUsualRoomTheLatestStart() {
        val groups = all.with("k", PlayState.PLAYING, 300).with("l", PlayState.PLAYING, 200)
        assertThat(homePick(groups, "Bedroom-id", null)?.group?.id).isEqualTo("k")
    }

    @Test
    fun nonePlayingTheUsualRoomSaysNothingPlaying() {
        assertThat(homePick(all, "Lounge-id", null)).isEqualTo(HomePick(lounge, nothingPlaying = true))
    }

    @Test
    fun noUsualRoomAndNothingPlayingTheFirstSonosLists() {
        assertThat(homePick(all, null, null)).isEqualTo(HomePick(kitchen, nothingPlaying = true))
        assertThat(homePick(all, "gone-id", null)?.group?.id).isEqualTo("k")
    }

    @Test
    fun aTransitioningGroupCountsAsPlaying() {
        assertThat(homePick(all.with("b", PlayState.TRANSITIONING), "Kitchen-id", null)?.group?.id).isEqualTo("b")
    }

    /** 6 §2.2. Fails if a paused group Home showed is swapped for the usual room, or a stopped one is kept. */
    @Test
    fun aPausedGroupHomeShowedStaysUntilItStops() {
        assertThat(homePick(all.with("b", PlayState.PAUSED), "Kitchen-id", keptId = "b")).isEqualTo(HomePick(all.with("b", PlayState.PAUSED)[2], false))
        assertThat(homePick(all.with("b", PlayState.STOPPED), "Kitchen-id", keptId = "b")?.group?.id).isEqualTo("k")
        assertThat(homePick(all.with("l", PlayState.PLAYING).with("b", PlayState.PAUSED), "Kitchen-id", keptId = "b")?.group?.id).isEqualTo("l")
    }

    @Test
    fun noGroupsNoPick() {
        assertThat(homePick(emptyList(), "Kitchen-id", null)).isNull()
    }

    /** 6 §5.2. Fails if the rest leave Sonos's order. */
    @Test
    fun theTabPutsPlayingGroupsFirstThenSonossOrder() {
        val groups = all.with("b", PlayState.PLAYING).with("l", PlayState.PAUSED)
        assertThat(tabOrder(groups).map { it.id }).containsExactly("b", "k", "l").inOrder()
    }

    private val bluetooth = NowPlaying.Input("Bluetooth")

    private fun List<SpeakerGroup>.playing(id: String, nowPlaying: NowPlaying, state: PlayState = PlayState.PLAYING) =
        map { if (it.id == id) it.copy(state = state, nowPlaying = nowPlaying) else it }

    /** The Office fault: an idle Bluetooth input reports PLAYING and must not beat the usual room. */
    @Test
    fun aPlayingInputWithNoTrackLosesToTheUsualRoom() {
        val groups = all.playing("b", bluetooth).playing("k", SONG, PlayState.PAUSED)
        assertThat(homePick(groups, "Kitchen-id", null)).isEqualTo(HomePick(groups[0], nothingPlaying = false))
        val idle = all.playing("b", bluetooth)
        assertThat(homePick(idle, "Bedroom-id", null)).isEqualTo(HomePick(idle.single { it.id == "b" }, nothingPlaying = true))
    }

    /** 6 §2.2: the Music tab shows the usual room paused with its track, so Home does too, even after a restart. */
    @Test
    fun theUsualRoomPausedWithATrackIsShownPausedWithNoKeptGroup() {
        val groups = all.playing("l", SONG, PlayState.PAUSED)
        assertThat(homePick(groups, "Dining room-id", null)).isEqualTo(HomePick(groups[1], nothingPlaying = false))
        assertThat(homePick(groups, null, null)?.nothingPlaying).isTrue()
        val firstPaused = all.playing("k", SONG, PlayState.PAUSED)
        assertThat(homePick(firstPaused, null, null)).isEqualTo(HomePick(firstPaused[0], nothingPlaying = false))
    }

    @Test
    fun theUsualRoomStoppedOrEmptyStillSaysNothingPlaying() {
        assertThat(homePick(all.playing("l", SONG, PlayState.STOPPED), "Lounge-id", null)?.nothingPlaying).isTrue()
        assertThat(homePick(all.playing("l", SONG, PlayState.PAUSED).map { if (it.id == "l") it.copy(nowPlaying = null) else it }, "Lounge-id", null)?.nothingPlaying).isTrue()
    }

    /** Fails if a kept group paused with nothing loaded is shown as paused, when the same group as the fallback says Nothing playing. */
    @Test
    fun aKeptGroupPausedWithNothingLoadedSaysNothingPlaying() {
        val groups = all.map { if (it.id == "b") it.copy(state = PlayState.PAUSED, nowPlaying = null) else it }
        assertThat(homePick(groups, "Bedroom-id", keptId = "b")).isEqualTo(HomePick(groups.single { it.id == "b" }, nothingPlaying = true))
        assertThat(homePick(groups, "Kitchen-id", keptId = "b")?.nothingPlaying).isTrue()
    }

    @Test
    fun aPlayingInputWithNoTrackLosesToAnotherPlayingGroup() {
        val groups = all.playing("b", bluetooth).playing("l", SONG)
        assertThat(homePick(groups, "Bedroom-id", null)?.group?.id).isEqualTo("l")
    }

    @Test
    fun aTrackOverAnInputStillPlays() {
        val groups = all.playing("b", SONG)
        assertThat(homePick(groups, "Kitchen-id", null)).isEqualTo(HomePick(groups[2], nothingPlaying = false))
    }

    @Test
    fun theTabDoesNotPutAnIdleInputFirst() {
        val groups = all.playing("b", bluetooth).playing("l", SONG)
        assertThat(tabOrder(groups).map { it.id }).containsExactly("l", "k", "b").inOrder()
    }
}
