package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.ui.ArtCache
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class MusicCapabilityTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { musicSettings(folder.root, scope) }

    @After
    fun tearDown() = scope.cancel()

    private fun capability(speakers: Speakers) = MusicCapability(
        speakers, MusicView(speakers, settings, scope), MusicActions({ speakers.system.value }, scope),
        MusicChecks(speakers.system, MutableStateFlow(true), scope), ArtCache { null },
        settings, MusicConnect(speakers.providers.firstOrNull()) { id, link -> settings.connect(id, link) }, RecordingToaster(),
    )

    /** 6 §3.1: rail slot 30, "Music", `speaker`. */
    @Test
    fun itIsMusicAtSlotThirty() {
        val music = capability(Speakers(setOf(TestMusicProvider()), settings, scope))
        assertThat(listOf(music.id, music.label, music.icon, music.order)).containsExactly("music", "Music", Icons.SPEAKER, 30).inOrder()
    }

    /** 6 §3.1: the tab once a connection exists. Fails if the tab shows before Find Sonos. */
    @Test
    fun theTabShowsOnceSpeakersAreConnected() = runTest {
        val music = capability(Speakers(setOf(TestMusicProvider()), settings, scope))
        assertThat(music.hasTab.first()).isFalse()
        settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
        withContext(Dispatchers.Default) { withTimeout(5_000) { music.hasTab.first { it } } }
    }

    /** 6 §5.1: REGULAR, after the forecast (40), only once connected. */
    @Test
    fun theHomeCardShowsOnceConnected() = runTest {
        val music = capability(Speakers(setOf(TestMusicProvider()), settings, scope))
        assertThat(music.cards().first()).isEmpty()
        settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
        val card = withContext(Dispatchers.Default) { withTimeout(5_000) { music.cards().first { it.isNotEmpty() } } }.single()
        assertThat(listOf(card.id, card.size, card.priority)).containsExactly("music.now", HomeCardSize.REGULAR, 30).inOrder()
    }

    /** 6 §5.4: Settings › Music after Calendars (400), before Door (500); none in a build with no provider. */
    @Test
    fun theSettingsPageIsMusicAt450() {
        val page = capability(Speakers(setOf(TestMusicProvider()), settings, scope)).settingsPages().single()
        assertThat(listOf(page.id, page.title, page.order)).containsExactly("music", "Music", 450).inOrder()
        assertThat(capability(Speakers(emptySet(), settings, scope)).settingsPages()).isEmpty()
    }
}
