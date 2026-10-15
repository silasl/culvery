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

@RunWith(AndroidJUnit4::class)
class SpeakersTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { musicSettings(folder.root, scope) }
    private val sonos = TestMusicProvider()
    private val home = MusicLink("Sonos_home", "192.168.1.20:1400")
    private val other = MusicLink("Sonos_other", "192.168.1.40:1400")

    @After
    fun tearDown() = scope.cancel()

    // On a real dispatcher: the opening follows DataStore's writes, which the test scheduler doesn't drive.
    private suspend fun Speakers.opensAnother(than: MusicSystem?) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { system.first { it != null && it !== than } } }

    @Test
    fun noConnectionOpensNothingUntilOneIsSaved() = runTest {
        val speakers = Speakers(setOf(sonos), settings, scope)
        settings.prefs.first()
        assertThat(speakers.system.value).isNull()
        settings.connect("music.sonos", home)
        speakers.opensAnother(than = null)
        assertThat(sonos.opened).containsExactly(home)
    }

    /** Review Focus 3: the open system found the household at a new address itself; saving it must not reopen it. */
    @Test
    fun aNewAddressForTheSameHouseholdKeepsTheOpenSystem() = runTest {
        settings.connect("music.sonos", home)
        val speakers = Speakers(setOf(sonos), settings, scope)
        val first = speakers.opensAnother(than = null)
        sonos.onMoved!!(home.copy(address = "192.168.1.21:1400"))
        assertThat(settings.prefs.first().address).isEqualTo("192.168.1.21:1400")
        settings.connect("music.sonos", other)
        speakers.opensAnother(than = first)
        // Had the move reopened the system, home would be listed twice.
        assertThat(sonos.opened).containsExactly(home, other).inOrder()
    }

    /** A connection to a provider this build doesn't have opens nothing; a later Sonos one opens. */
    @Test
    fun aConnectionToAProviderNotInTheBuildOpensNothing() = runTest {
        settings.connect("music.cloud", MusicLink("cloud-household", "cloud"))
        val speakers = Speakers(setOf(sonos), settings, scope)
        settings.prefs.first { it.providerId == "music.cloud" }
        assertThat(speakers.system.value).isNull()
        settings.connect("music.sonos", home)
        speakers.opensAnother(than = null)
        assertThat(sonos.opened).containsExactly(home)
    }
}
