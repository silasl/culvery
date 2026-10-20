package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §5.5: what TalkBack says for a group and its controls. */
class MusicWordsTest {
    private val kitchen = group("k", "Kitchen")

    @Test
    fun aGroupIsSpokenWithWhatItPlays() {
        assertThat(groupSpoken(kitchen.copy(state = PlayState.PLAYING, nowPlaying = SONG))).isEqualTo("Kitchen, playing Song by Artist")
        assertThat(groupSpoken(kitchen.copy(state = PlayState.PAUSED, nowPlaying = SONG))).isEqualTo("Kitchen, paused")
        assertThat(groupSpoken(kitchen)).isEqualTo("Kitchen, nothing playing")
        assertThat(groupSpoken(kitchen.copy(state = PlayState.PAUSED, nowPlaying = SONG), nothingPlaying = true)).isEqualTo("Kitchen, nothing playing")
        assertThat(groupSpoken(group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION)))
            .isEqualTo("Lounge + Dining room, playing Culvery FM, The Breakfast Show")
        assertThat(groupSpoken(kitchen.copy(state = PlayState.PLAYING, nowPlaying = NowPlaying.Track("Song", null, null)))).isEqualTo("Kitchen, playing Song")
    }

    @Test
    fun anInputIsNamedAndSpokenWithoutAProgramme() {
        val bluetooth = kitchen.copy(state = PlayState.PLAYING, nowPlaying = NowPlaying.Input("Bluetooth"))
        assertThat(nowPlayingLines(bluetooth, nothingPlaying = false)).isEqualTo("Bluetooth" to null)
        assertThat(groupSpoken(bluetooth)).isEqualTo("Kitchen, playing Bluetooth")
    }

    @Test
    fun theControlsWords() {
        assertThat(listOf(pauseSpoken("Kitchen"), playSpoken("Kitchen"), nextSpoken("Kitchen"), shuffleSpoken("Kitchen"), "${volumeName("Kitchen")}, ${percentWords(30)}"))
            .containsExactly("Pause Kitchen", "Play Kitchen", "Next track in Kitchen", "Shuffle everything in Kitchen", "Kitchen volume, 30 percent").inOrder()
    }

    @Test
    fun theLinesShown() {
        assertThat(nowPlayingLines(kitchen.copy(nowPlaying = SONG), nothingPlaying = false)).isEqualTo("Song" to "Artist")
        assertThat(nowPlayingLines(kitchen.copy(nowPlaying = STATION), nothingPlaying = false)).isEqualTo("Culvery FM" to "The Breakfast Show")
        assertThat(nowPlayingLines(kitchen.copy(nowPlaying = SONG), nothingPlaying = true)).isEqualTo("Nothing playing" to null)
    }

    @Test
    fun musicSummaryCountsRoomsAndMultiRoomGroups() {
        val lounge = group("l", "Lounge", "Dining room")
        assertThat(musicSummary(listOf(lounge, kitchen, group("b", "Bedroom")))).isEqualTo("4 rooms · 1 group · Sonos")
        assertThat(musicSummary(listOf(lounge, group("g", "Garden", "Hall", "Study")))).isEqualTo("5 rooms · 2 groups · Sonos")
        assertThat(musicSummary(listOf(kitchen, group("b", "Bedroom")))).isEqualTo("2 rooms · Sonos")
        assertThat(musicSummary(listOf(kitchen))).isEqualTo("1 room · Sonos")
    }

    @Test
    fun theGroupChipAndPlayLastWords() {
        assertThat(roomsChip(2)).isEqualTo("2 rooms")
        assertThat(PLAY_LAST).isEqualTo("Play last")
        assertThat(playLastSpoken("Bedroom")).isEqualTo("Play last in Bedroom")
    }
}
