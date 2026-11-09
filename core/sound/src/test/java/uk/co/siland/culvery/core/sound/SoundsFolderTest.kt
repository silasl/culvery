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

    /** Fails if a theme folder's sound is dropped for sharing a name with a main folder sound. */
    @Test
    fun aThemeSoundSharingAMainFolderNameIsKept() {
        touch("howl.mp3")
        touch("halloween/Howl.mp3")
        assertThat(readSoundsFolder(folder.root).map { it.id }).containsExactly("file:howl.mp3", "file:halloween/Howl.mp3").inOrder()
    }

    /** Fails if a capitalised theme folder isn't read as that theme. */
    @Test
    fun aThemeFolderIsFoundWhateverItsCase() {
        touch("Halloween/howl.mp3")
        val sound = readSoundsFolder(folder.root).single()
        assertThat(sound.theme).isEqualTo(SeasonalTheme.HALLOWEEN)
        assertThat(sound.id).isEqualTo("file:Halloween/howl.mp3")
    }

    /** Fails if two main folder names that differ only by case both show, or the first by path isn't the one kept. */
    @Test
    fun sameNamesDifferingInCaseShowOnce() {
        touch("Howl.mp3")
        touch("howl.wav")
        assertThat(readSoundsFolder(folder.root).map { it.id }).containsExactly("file:Howl.mp3")
    }

    /** Fails if two names in one theme folder that differ only by case both show. */
    @Test
    fun sameNamesInAThemeFolderShowOnceToo() {
        touch("halloween/Howl.mp3")
        touch("halloween/howl.wav")
        assertThat(readSoundsFolder(folder.root).map { it.id }).containsExactly("file:halloween/Howl.mp3")
    }

    /** Fails if a formatting character makes a repeated name show twice. */
    @Test
    fun namesDifferingOnlyByAFormattingCharacterShowOnce() {
        touch("Howl.mp3")
        touch("Ho\u200Bwl.wav")
        assertThat(readSoundsFolder(folder.root).single().id).isEqualTo("file:Howl.mp3")
    }

    /** Fails if a formatting or control character survives, or a line break or tab fails to become a space. */
    @Test
    fun formattingAndControlCharactersAreDropped() {
        assertThat(cleanSoundName("\u202EHo\u200Bwl\uFEFF\u0007")).isEqualTo("Howl")
        assertThat(cleanSoundName("a\u2028b\u2029c")).isEqualTo("a b c")
        assertThat(cleanSoundName("Door\tbell")).isEqualTo("Door bell")
        assertThat(cleanSoundName("a\nb\rc\u000Bd\u000Ce\u0007f")).isEqualTo("a b c d ef")
    }

    /** Fails if runs of spaces stay or the ends aren't trimmed. */
    @Test
    fun runsOfSpacesCollapseAndTheEndsAreTrimmed() {
        assertThat(cleanSoundName("  Test   Honk\u00A0 two ")).isEqualTo("Test Honk two")
    }

    /** Fails if a long name isn't cut to 40 characters ending in an ellipsis, or a 40-character name is cut. */
    @Test
    fun aLongNameIsCutToFortyWithAnEllipsis() {
        val cleaned = cleanSoundName("x".repeat(60))
        assertThat(cleaned).isEqualTo("x".repeat(SOUND_NAME_MAX - 1) + "\u2026")
        assertThat(cleanSoundName("x".repeat(SOUND_NAME_MAX))).isEqualTo("x".repeat(SOUND_NAME_MAX))
    }

    /** Fails if the cut splits a surrogate pair. */
    @Test
    fun anEmojiAtTheCutIsNotSplit() {
        val cleaned = cleanSoundName("x".repeat(38) + "\uD83D\uDE00".repeat(5))
        assertThat(cleaned).isEqualTo("x".repeat(38) + "\uD83D\uDE00" + "\u2026")
        assertThat(cleaned.codePointCount(0, cleaned.length)).isEqualTo(SOUND_NAME_MAX)
    }

    /** Fails if two names that differ only past the cut are judged the same. */
    @Test
    fun longNamesDifferingPastTheCutBothShow() {
        touch("x".repeat(44) + "a.mp3")
        touch("x".repeat(44) + "b.mp3")
        val sounds = readSoundsFolder(folder.root)
        assertThat(sounds).hasSize(2)
        assertThat(sounds.map { it.name }.distinct()).hasSize(1)
    }

    /** Fails if a name with nothing left isn't Unnamed sound. */
    @Test
    fun aNameWithNothingLeftIsUnnamed() {
        assertThat(cleanSoundName("\u200B\u202E \u0000")).isEqualTo(UNNAMED_SOUND)
        assertThat(cleanSoundName("")).isEqualTo(UNNAMED_SOUND)
    }

    /** Fails if cleaning a name changes the stored id, or the gone-file name disagrees with the listed name. */
    @Test
    fun aCleanedNameKeepsTheStoredId() {
        touch("Ho\u200Bwl.wav")
        val sound = readSoundsFolder(folder.root).single()
        assertThat(sound.name).isEqualTo("Howl")
        assertThat(sound.id).isEqualTo("file:Ho\u200Bwl.wav")
        assertThat(soundNameFromId(sound.id)).isEqualTo("Howl")
    }

    /** Fails if the Sad trombone default misses a file whose name has a hidden character. */
    @Test
    fun theTromboneIsFoundByItsCleanedName() {
        touch("Sad\u200B trombone.mp3")
        assertThat(readSoundsFolder(folder.root).soundFor(SAD_TROMBONE_ID)!!.id).isEqualTo("file:Sad\u200B trombone.mp3")
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
