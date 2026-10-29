package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.test.soon

@RunWith(AndroidJUnit4::class)
class SpeakersTest {
    @get:Rule val music = MusicFixture()
    private val scope = music.scope
    private val settings get() = music.settings
    private val sonos = TestMusicProvider()
    private val home = MusicLink("Sonos_home", "192.168.1.20:1400")
    private val other = MusicLink("Sonos_other", "192.168.1.40:1400")

    // On a real dispatcher: the opening follows DataStore's writes, which the test scheduler doesn't drive.
    private suspend fun Speakers.opensAnother(than: MusicSystem?) = soon { system.first { it != null && it !== than } }

    @Test
    fun noConnectionOpensNothingUntilOneIsSaved() = runTest {
        val speakers = Speakers(setOf(sonos), settings, scope)
        settings.prefs.first()
        assertThat(speakers.system.value).isNull()
        settings.connect("music.sonos", home)
        speakers.opensAnother(than = null)
        assertThat(sonos.opened).containsExactly(home)
    }

    /** 6 §4.1: the open system found the household at a new address itself; saving it must not reopen it. */
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

    /** 6 §4.1: the speakers moved and the scan missed them; the address typed for the same household is the one used next. */
    @Test
    fun aTypedAddressForTheSameHouseholdIsUsedByTheOpenSystem() = runTest {
        settings.connect("music.sonos", home)
        val speakers = Speakers(setOf(sonos), settings, scope)
        speakers.opensAnother(than = null)
        sonos.speakers["192.168.1.30"] = home.copy(address = "192.168.1.30:1400")
        assertThat(MusicConnect(speakers, settings).enterAddress("192.168.1.30")).isEqualTo(ConnectOutcome.Connected)
        assertThat(soon { sonos.systems.single().addresses.first { it.isNotEmpty() } }).containsExactly("192.168.1.30:1400")
        assertThat(sonos.opened).containsExactly(home)
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

    /** 13 §8: a debug sample bound ahead of Sonos is never what Find Sonos or a typed address asks. */
    @Test
    fun aSampleListedFirstIsNotWhatFindAsks() = runTest {
        val sample = TestMusicProvider("sample.music", connectable = false)
        sonos.found = home
        val speakers = Speakers(linkedSetOf(sample, sonos), settings, scope)
        assertThat(MusicConnect(speakers, settings).find()).isEqualTo(ConnectOutcome.Connected)
        assertThat(sample.asked).isEmpty()
        assertThat(sonos.asked).containsExactly("find")
    }
}
