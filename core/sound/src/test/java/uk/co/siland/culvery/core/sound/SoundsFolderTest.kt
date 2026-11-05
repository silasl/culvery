package uk.co.siland.culvery.core.sound

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.co.siland.culvery.core.ui.SeasonalTheme

class SoundsFolderTest {
    @get:Rule val folder = TemporaryFolder()

    private fun touch(path: String) {
        val file = File(folder.root, path)
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0))
    }

    @Test
    fun aFileIsNamedWithoutItsExtension() {
        touch("Test Honk.wav")
        val sound = readSoundsFolder(folder.root).single()
        assertThat(sound.name).isEqualTo("Test Honk")
        assertThat(sound.id).isEqualTo("file:Test Honk.wav")
        assertThat(sound.theme).isNull()
    }

    @Test
    fun aThemeFolderTagsItsFiles() {
        touch("halloween/Test Howl.ogg")
        val sound = readSoundsFolder(folder.root).single()
        assertThat(sound.theme).isEqualTo(SeasonalTheme.HALLOWEEN)
        assertThat(sound.id).isEqualTo("file:halloween/Test Howl.ogg")
    }

    @Test
    fun otherFilesAndFoldersAreIgnored() {
        touch("notes.txt")
        touch(".x.wav")
        touch("other/Test.wav")
        touch("halloween/deeper/Test.wav")
        assertThat(readSoundsFolder(folder.root)).isEmpty()
    }

    @Test
    fun extensionsIgnoreCase() {
        touch("TEST.MP3")
        assertThat(readSoundsFolder(folder.root).map { it.id }).containsExactly("file:TEST.MP3")
    }

    @Test
    fun soundsAreSortedByPathIgnoringCase() {
        touch("b.wav")
        touch("A.wav")
        touch("halloween/c.wav")
        assertThat(readSoundsFolder(folder.root).map { it.id })
            .containsExactly("file:A.wav", "file:b.wav", "file:halloween/c.wav").inOrder()
    }

    @Test
    fun aSharedNameKeepsTheFirstPath() {
        touch("Test Honk.wav")
        touch("halloween/Test Honk.mp3")
        assertThat(readSoundsFolder(folder.root).map { it.id }).containsExactly("file:Test Honk.wav")
    }

    @Test
    fun noFolderIsNoSounds() {
        assertThat(readSoundsFolder(null)).isEmpty()
        assertThat(readSoundsFolder(File(folder.root, "missing"))).isEmpty()
    }

    @Test
    fun logsLeaveOutTheNameAndTheFile() {
        touch("Test Honk.wav")
        assertThat(readSoundsFolder(folder.root).single().toString()).doesNotContain("Honk")
    }

    @Test
    fun aGoneFileIsNamedAsTheFolderNamedIt() {
        touch("Test Honk.wav")
        touch("halloween/Test v2.Howl.ogg")
        readSoundsFolder(folder.root).forEach { assertThat(soundNameFromId(it.id)).isEqualTo(it.name) }
        assertThat(soundNameFromId(BuiltInSound.CLASSIC.id)).isNull()
        assertThat(soundNameFromId("Test Honk.wav")).isNull()
    }
}
