package uk.co.siland.culvery.capability.party

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.sound.SAD_TROMBONE_ID
import uk.co.siland.culvery.core.sound.soundFor

/** 18 §4.2, §4.3: what each pad holds, and which pads show. */
@RunWith(AndroidJUnit4::class)
class SoundPadsTest {
    private val memory = MemoryStore(emptyPreferences())
    private val store = SoundPadsStore(memory)

    private fun shown(stored: List<String?>, room: Int = 3, birthday: Boolean = false) =
        shownPads(stored, room, ::inLibrary, birthday).map { it.pad to it.sound.id }

    /** Fails if a fresh file reads anything but the Sad trombone default, Haunted organ and None. */
    @Test
    fun defaultsAreTromboneOrganNone() = runTest {
        assertThat(store.pads.first()).containsExactly(SAD_TROMBONE_ID, ORGAN.id, null).inOrder()
    }

    /** Fails if the default, or a stored built-in Sad trombone, doesn't find the tablet's clip, or shows a pad when the folder has none. */
    @Test
    fun theTromboneDefaultIsTheTabletsClipIfAny() {
        assertThat(shown(listOf(SAD_TROMBONE_ID, null, null))).containsExactly(1 to TROMBONE.id)
        assertThat(shownPads(listOf(SAD_TROMBONE_ID, null, null), 3, (LIBRARY - TROMBONE)::soundFor, false)).isEmpty()
    }

    /** Fails if None isn't written as "none" under its pad's key, so it reads back as the default, or another pad changes. */
    @Test
    fun noneIsStored() = runTest {
        store.setPad(1, null)
        store.setPad(3, HONK.id)

        assertThat(store.pads.first()).containsExactly(null, ORGAN.id, HONK.id).inOrder()
        assertThat(memory.state.value!![stringPreferencesKey("pad_1")]).isEqualTo("none")
        assertThat(memory.state.value!![stringPreferencesKey("pad_3")]).isEqualTo(HONK.id)
    }

    /** 18 Ruling 2: fails if pad 3 shows with room for two, or a later pad takes its place. */
    @Test
    fun taekwondoKeepsPadThreeWaiting() {
        assertThat(shown(listOf(TROMBONE.id, ORGAN.id, HONK.id), room = 2))
            .containsExactly(1 to TROMBONE.id, 2 to ORGAN.id).inOrder()
        assertThat(shown(listOf(null, ORGAN.id, HONK.id), room = 2)).containsExactly(2 to ORGAN.id)
    }

    /** Fails if a pad set to a file no longer on the tablet, or to None, shows, or the others don't close up. */
    @Test
    fun aGoneFileHidesItsPad() {
        assertThat(shown(listOf("file:Test Gone.wav", null, HONK.id))).containsExactly(3 to HONK.id)
    }

    /** 18 Ruling 4: fails if pad 1 isn't Happy Birthday on a birthday, set to a sound or to None. */
    @Test
    fun aBirthdayMakesPadOneHappyBirthday() {
        assertThat(shown(listOf(TROMBONE.id, null, null), birthday = true)).containsExactly(1 to BIRTHDAY_SONG.id)
        assertThat(shown(listOf(null, null, null), birthday = true)).containsExactly(1 to BIRTHDAY_SONG.id)
        assertThat(shown(listOf(null, null, null), room = 0, birthday = true)).isEmpty()
    }

    /** Fails if a birthday changes pad 2 or 3. */
    @Test
    fun otherPadsKeepTheirSoundsOnABirthday() {
        assertThat(shown(listOf(TROMBONE.id, ORGAN.id, HONK.id), birthday = true))
            .containsExactly(1 to BIRTHDAY_SONG.id, 2 to ORGAN.id, 3 to HONK.id).inOrder()
    }
}
