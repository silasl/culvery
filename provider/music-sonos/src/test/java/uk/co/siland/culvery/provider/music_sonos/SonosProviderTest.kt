package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class SonosProviderTest {
    private val speaker = FakeSpeaker()
    private val printer = FakeSpeaker()
    private val client = SonosClient(testHttp(), onThisMachine) { _, _ -> }
    private var online = true
    private var heard = listOf<String>()
    private var scans = 0

    private fun provider(port: Int = speaker.port) = SonosProvider(client, { scans++; heard }, { online }, WallClock { 0L }, port)

    @After
    fun tearDown() {
        speaker.close()
        printer.close()
    }

    @Test
    fun itIsSonosAndTheHouseholdCanConnectIt() {
        val d = provider().descriptor
        assertThat(listOf(d.id, d.displayName, d.userConnectable)).containsExactly("music.sonos", "Sonos", true).inOrder()
    }

    /** 6 §4.1: any speaker that answers gives the household; something else on the network that answered SSDP doesn't. */
    @Test
    fun findKeepsTheFirstThatSaysItsHousehold() = runTest {
        speaker.answer("GetHouseholdID", SonosFixtures.household())
        heard = listOf(printer.address, speaker.address)
        assertThat(provider().find()).isEqualTo(MusicLink(SonosFixtures.HOUSEHOLD, speaker.address))
    }

    @Test
    fun findWithNothingAnsweringIsNull() = runTest {
        assertThat(provider().find()).isNull()
        assertThat(scans).isEqualTo(1)
    }

    @Test
    fun offlineSaysSoWithoutScanning() = runTest {
        online = false
        assertThat(throws<MusicUnreachableException> { provider().find() }.problem).isEqualTo(MusicProblem.OFFLINE)
        assertThat(scans).isEqualTo(0)
    }

    /** 6 §4.1: "Enter a speaker’s address", checked by asking it; the port is Sonos's. */
    @Test
    fun checkAsksTheSpeakerAtThatAddress() = runTest {
        speaker.answer("GetHouseholdID", SonosFixtures.household())
        assertThat(provider().check(speaker.host)).isEqualTo(MusicLink(SonosFixtures.HOUSEHOLD, "${speaker.host}:${speaker.port}"))
        assertThat(provider(port = printer.port).check(printer.host)).isNull()
        // Not a speaker on this test's rule, so never asked.
        assertThat(provider().check("8.8.8.8")).isNull()
    }
}
