package uk.co.siland.culvery.core.sound

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.ui.SeasonalTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SoundLibraryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun touch(path: String) {
        val file = File(folder.root, path)
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0))
    }

    private fun library() = SoundLibrary({ folder.root }, CoroutineScope(UnconfinedTestDispatcher()), UnconfinedTestDispatcher())

    /** Regression guard (18 §2.2): the page's re-read wins over a start-up read that was slower to finish. */
    @Test
    fun aLaterRefreshWinsOverAHeldFirstRead() = runBlocking {
        val empty = folder.newFolder("empty")
        val reading = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val start = SupervisorJob()
        val library = SoundLibrary({
            if (first.getAndSet(false)) {
                reading.countDown()
                finish.await()
                empty
            } else folder.root
        }, CoroutineScope(start + Dispatchers.Unconfined), Dispatchers.IO)
        reading.await()
        touch("Test Honk.wav")
        library.refresh()
        finish.countDown()
        start.children.forEach { it.join() }
        assertThat(library.sounds.value.map { it.id }).contains("file:Test Honk.wav")
    }

    /** Fails if firstReadDone is true before the first read lands, or false after. */
    @Test
    fun theFirstReadSaysWhenItLands() = runBlocking {
        val reading = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val start = SupervisorJob()
        val library = SoundLibrary({
            reading.countDown()
            finish.await()
            folder.root
        }, CoroutineScope(start + Dispatchers.Unconfined), Dispatchers.IO)
        reading.await()
        assertThat(library.firstReadDone.value).isFalse()
        finish.countDown()
        start.children.forEach { it.join() }
        assertThat(library.firstReadDone.value).isTrue()
    }

    @Test
    fun aFailedReadKeepsTheListAndCountsAsLanded() = runTest {
        touch("Test Honk.wav")
        var failing = false
        val library = SoundLibrary({ if (failing) throw IllegalStateException("no folder") else folder.root }, CoroutineScope(UnconfinedTestDispatcher()), UnconfinedTestDispatcher())
        val before = library.sounds.value
        assertThat(before.map { it.id }).contains("file:Test Honk.wav")
        ShadowLog.clear()
        failing = true
        library.refresh()
        assertThat(library.sounds.value).isEqualTo(before)
        assertThat(library.firstReadDone.value).isTrue()
        val warnings = ShadowLog.getLogsForTag("SoundLibrary").filter { it.type == Log.WARN }
        assertThat(warnings.map { it.msg }).containsExactly("The sounds folder couldn't be read (IllegalStateException)")
    }

    @Test
    fun aFailedFirstReadStillCountsAsLanded() {
        val library = SoundLibrary({ throw IllegalStateException("no folder") }, CoroutineScope(UnconfinedTestDispatcher()), UnconfinedTestDispatcher())
        assertThat(library.firstReadDone.value).isTrue()
        assertThat(library.sounds.value.map { it.id }).containsExactlyElementsIn(BuiltInSound.entries.map { it.id })
    }

    @Test
    fun builtInsComeFirst() {
        touch("Test Honk.wav")
        val ids = library().sounds.value.map { it.id }
        assertThat(ids.take(BuiltInSound.entries.size)).containsExactlyElementsIn(BuiltInSound.entries.map { it.id }).inOrder()
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

    /** 21 §3.8: a file in the tablet's christmas folder wins over the built-in music box. */
    @Test
    fun aChristmasFileBeatsTheMusicBox() {
        assertThat(library().forTheme(SeasonalTheme.CHRISTMAS).id).isEqualTo("builtin:christmas_music_box")
        touch("christmas/Test Bells.ogg")
        assertThat(library().forTheme(SeasonalTheme.CHRISTMAS).id).isEqualTo("file:christmas/Test Bells.ogg")
    }

    /** **Pin** (21 F14): themed built-ins stay in the list both Settings pickers read. */
    @Test
    fun themedSoundsStayChoices() = runTest {
        val ids = library().choices.first().map { it.id }
        assertThat(ids).containsAtLeast("builtin:organ", "builtin:christmas_music_box")
    }

    @Test
    fun noThemeIsTheClassicChime() {
        assertThat(library().forTheme(null).id).isEqualTo(BuiltInSound.CLASSIC.id)
    }

    @Test
    fun theOldBuiltInTromboneIsTheTabletsClip() {
        assertThat(library().byId(SAD_TROMBONE_ID)).isNull()
        touch("Sad trombone.mp3")
        assertThat(library().byId(SAD_TROMBONE_ID)!!.id).isEqualTo("file:Sad trombone.mp3")
    }

    @Test
    fun byIdOfAGoneFileIsNull() {
        assertThat(library().byId("file:Gone.wav")).isNull()
        assertThat(library().byId(null)).isNull()
    }
}
