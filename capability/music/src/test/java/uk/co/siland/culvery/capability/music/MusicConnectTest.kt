package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 6 §4.1, §6: Find Sonos and a typed address, and what each says when it can't. */
@RunWith(AndroidJUnit4::class)
class MusicConnectTest {
    private val sonos = TestMusicProvider()
    private val saved = mutableListOf<Pair<String, MusicLink>>()
    private val home = MusicLink("Sonos_home", "192.168.1.20:1400")

    private fun connect(provider: MusicProvider? = sonos, failSave: Boolean = false) = MusicConnect(provider) { id, link ->
        if (failSave) throw IOException("disk full")
        saved += id to link
    }

    @Test
    fun findSavesWhatItFinds() = runTest {
        sonos.found = home
        assertThat(connect().find()).isEqualTo(ConnectOutcome.Connected)
        assertThat(saved).containsExactly("music.sonos" to home)
    }

    @Test
    fun nothingFoundSaysWhatToDo() = runTest {
        assertThat(connect().find()).isEqualTo(ConnectOutcome.Failed("No Sonos speakers answered. Check they’re on, or enter a speaker’s address."))
        assertThat(saved).isEmpty()
    }

    @Test
    fun offlineSaysSo() = runTest {
        sonos.failure = MusicUnreachableException(MusicProblem.OFFLINE)
        assertThat(connect().find()).isEqualTo(ConnectOutcome.Failed("The tablet is offline. Check the Wi-Fi."))
    }

    @Test
    fun aTypedAddressIsTidiedCheckedAndSaved() = runTest {
        sonos.speakers["192.168.1.20"] = home
        assertThat(connect().enterAddress(" 192.168.001.020 ")).isEqualTo(ConnectOutcome.Connected)
        assertThat(sonos.asked).containsExactly("check 192.168.1.20")
        assertThat(saved).containsExactly("music.sonos" to home)
    }

    /** 6 §6: nothing is asked of a mistyped or off-network address. */
    @Test
    fun aMistypedAddressContactsNothing() = runTest {
        val music = connect()
        val howToType = ConnectOutcome.Failed("Type the address as four numbers, like 192.168.1.20.")
        assertThat(music.enterAddress("192.168.1.20:1400")).isEqualTo(howToType)
        assertThat(music.enterAddress("8.8.8.8")).isEqualTo(howToType)
        assertThat(music.enterAddress("sonos.local")).isEqualTo(howToType)
        assertThat(sonos.asked).isEmpty()
    }

    @Test
    fun anAddressWithNoSpeakerSaysSo() = runTest {
        assertThat(connect().enterAddress("192.168.1.99"))
            .isEqualTo(ConnectOutcome.Failed("No Sonos speaker answered at that address. Check it in the Sonos app under About My System."))
    }

    /** A build with no music provider finds nothing rather than failing. */
    @Test
    fun noProviderFindsNothing() = runTest {
        assertThat(connect(provider = null).find()).isEqualTo(ConnectOutcome.Failed("No Sonos speakers answered. Check they’re on, or enter a speaker’s address."))
    }

    @Test
    fun aSaveThatFailsSaysSo() = runTest {
        sonos.found = home
        assertThat(connect(failSave = true).find()).isEqualTo(ConnectOutcome.Failed("Couldn’t save that change — try again."))
    }
}
