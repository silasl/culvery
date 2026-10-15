package uk.co.siland.culvery.provider.music_sonos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §3.2, §6: the speakers' addresses from SSDP's answers, only for speakers on the home network that sent them. */
class SsdpTest {
    private val zonePlayer = "urn:schemas-upnp-org:device:ZonePlayer:1"

    private fun answer(from: String, st: String, location: String, case: (String) -> String = { it }) = SsdpAnswer(
        from,
        "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age = 1800\r\nEXT:\r\n${case("LOCATION")}: $location\r\n" +
            "SERVER: Linux UPnP/1.0 Sonos/80.1-55014 (ZPS9)\r\n${case("ST")}: $st\r\nUSN: uuid:RINCON_000E58AAAAAA01400::$st\r\n\r\n",
    )

    /** Fails if a router's answer counts, a speaker is listed twice, or the order heard is lost. */
    @Test
    fun eachZonePlayerOnceInTheOrderHeard() {
        assertThat(
            ssdpAddresses(
                listOf(
                    answer("192.168.1.21", zonePlayer, "http://192.168.1.21:1400/xml/device_description.xml"),
                    answer("192.168.1.1", "urn:schemas-upnp-org:device:InternetGatewayDevice:1", "http://192.168.1.1:5000/rootDesc.xml"),
                    answer("192.168.1.20", zonePlayer, "http://192.168.1.20:1400/xml/device_description.xml"),
                    answer("192.168.1.21", zonePlayer, "http://192.168.1.21:1400/xml/device_description.xml"),
                    SsdpAnswer("192.168.1.30", "not an answer"),
                ),
            ),
        ).containsExactly("192.168.1.21:1400", "192.168.1.20:1400").inOrder()
    }

    @Test
    fun headersAreReadWhateverTheirCase() {
        assertThat(ssdpAddresses(listOf(answer("192.168.1.20", zonePlayer, "http://192.168.1.20:1400/xml/device_description.xml") { it.lowercase() })))
            .containsExactly("192.168.1.20:1400")
    }

    /** 6 §6 privacy: an answer whose LOCATION is off the home network, isn't its sender, or isn't on Sonos's port is dropped. */
    @Test
    fun anAnswerPointingElsewhereIsDropped() {
        assertThat(
            ssdpAddresses(
                listOf(
                    answer("192.168.1.20", zonePlayer, "http://8.8.8.8:1400/xml/device_description.xml"),
                    answer("192.168.1.20", zonePlayer, "http://192.168.1.99:1400/xml/device_description.xml"),
                    answer("192.168.1.20", zonePlayer, "http://192.168.1.20:80/xml/device_description.xml"),
                ),
            ),
        ).isEmpty()
    }
}
