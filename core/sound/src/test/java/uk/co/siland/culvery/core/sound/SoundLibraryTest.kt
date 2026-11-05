package uk.co.siland.culvery.core.sound

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
import uk.co.siland.culvery.core.ui.SeasonalTheme

@OptIn(ExperimentalCoroutinesApi::class)
class SoundLibraryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun touch(path: String) {
        val file = File(folder.root, path)
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0))
    }

    private fun library() = SoundLibrary({ folder.root }, CoroutineScope(UnconfinedTestDispatcher()), UnconfinedTestDispatcher())

    /** 18 §2.2: the page's re-read wins over a start-up read that was slower to finish. */
    @Test
    fun aSlowStartUpReadLeavesARefreshsSounds() = runBlocking {
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
