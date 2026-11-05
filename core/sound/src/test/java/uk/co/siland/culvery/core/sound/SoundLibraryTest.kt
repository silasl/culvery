package uk.co.siland.culvery.core.sound

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.co.siland.culvery.core.ui.SeasonalTheme

@OptIn(ExperimentalCoroutinesApi::class)
class SoundLibraryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun touch(path: String) {
        val file = File(folder.root, path)
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0))
    }

    private fun library() = SoundLibrary({ folder.root }, UnconfinedTestDispatcher())

    @Test
    fun builtInsComeFirst() {
        touch("Test Honk.wav")
        val ids = library().sounds.value.map { it.id }
        assertThat(ids.take(5)).containsExactlyElementsIn(BuiltInSound.entries.map { it.id }).inOrder()
        assertThat(ids.last()).isEqualTo("file:Test Honk.wav")
    }

    @Test
    fun refreshFindsANewFile() = runTest {
        val library = library()
        touch("Test Honk.wav")
        library.refresh()
        assertThat(library.sounds.value.map { it.id }).contains("file:Test Honk.wav")
        assertThat(library.byId("file:Test Honk.wav")!!.onTablet).isTrue()
    }

    @Test
    fun choicesLeaveOutHappyBirthday() = runTest {
        val ids = library().choices.first().map { it.id }
        assertThat(ids).doesNotContain(BuiltInSound.HAPPY_BIRTHDAY.id)
        assertThat(ids).contains(BuiltInSound.CLASSIC.id)
    }

    @Test
    fun aThemeFileBeatsTheBuiltIn() {
        assertThat(library().forTheme(SeasonalTheme.HALLOWEEN).id).isEqualTo(BuiltInSound.ORGAN.id)
        touch("halloween/Test Howl.ogg")
        assertThat(library().forTheme(SeasonalTheme.HALLOWEEN).id).isEqualTo("file:halloween/Test Howl.ogg")
    }

    @Test
    fun noThemeIsTheClassicChime() {
        assertThat(library().forTheme(null).id).isEqualTo(BuiltInSound.CLASSIC.id)
    }

    @Test
    fun byIdOfAGoneFileIsNull() {
        assertThat(library().byId("file:Gone.wav")).isNull()
        assertThat(library().byId(null)).isNull()
    }
}
