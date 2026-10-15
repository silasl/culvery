package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class MusicCapabilityTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { musicSettings(folder.root, scope) }

    @After
    fun tearDown() = scope.cancel()

    /** 6 §3.1: rail slot 30, "Music", `speaker`. */
    @Test
    fun itIsMusicAtSlotThirty() {
        val music = MusicCapability(Speakers(setOf(TestMusicProvider()), settings, scope))
        assertThat(listOf(music.id, music.label, music.icon, music.order)).containsExactly("music", "Music", Icons.SPEAKER, 30).inOrder()
    }

    /** 6 §3.1: the tab once a connection exists. Fails if the tab shows before Find Sonos. */
    @Test
    fun theTabShowsOnceSpeakersAreConnected() = runTest {
        val music = MusicCapability(Speakers(setOf(TestMusicProvider()), settings, scope))
        assertThat(music.hasTab.first()).isFalse()
        settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
        withContext(Dispatchers.Default) { withTimeout(5_000) { music.hasTab.first { it } } }
    }
}
