package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §4.1, §6: a typed address is four numbers on the home network, tidied; anything else contacts nothing. */
class SpeakerAddressTest {
    /** Fails if a port, a name, a public address or a fifth number gets through, or spaces and leading zeros don't. */
    @Test
    fun onlyFourNumbersInAPrivateRangeAreASpeakerAddress() {
        assertThat(speakerHost("192.168.1.20")).isEqualTo("192.168.1.20")
        assertThat(speakerHost(" 192.168.1.20 ")).isEqualTo("192.168.1.20")
        assertThat(speakerHost("192.168.001.020")).isEqualTo("192.168.1.20")
        assertThat(speakerHost("10.0.0.5")).isEqualTo("10.0.0.5")
        assertThat(speakerHost("172.16.4.2")).isEqualTo("172.16.4.2")
        assertThat(speakerHost("172.31.255.1")).isEqualTo("172.31.255.1")
        assertThat(speakerHost("169.254.10.1")).isEqualTo("169.254.10.1")
        listOf(
            "172.32.0.1", "8.8.8.8", "192.168.1", "192.168.1.20.1", "192.168.1.256", "192.168.1.20:1400", "sonos.local",
            "", "192.168.1.2O", "１９２.168.1.20", "192.168..20", "192.168.1.0020",
        ).forEach { assertThat(speakerHost(it)).isNull() }
    }
}
