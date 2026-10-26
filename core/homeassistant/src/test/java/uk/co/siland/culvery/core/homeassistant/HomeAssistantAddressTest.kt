package uk.co.siland.culvery.core.homeassistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 11 §4.1: the address as people type it. */
class HomeAssistantAddressTest {
    /** Fails if a bare address, a padded one or an upper-case scheme isn't turned into the one standard form. */
    @Test
    fun typedFormsBecomeTheStandardAddress() {
        listOf("192.168.1.20", " http://192.168.1.20:8123/ ", "HTTP://192.168.1.20").forEach {
            assertThat(homeAssistantUrl(it).toString()).isEqualTo("http://192.168.1.20:8123/")
        }
    }

    /** Fails if a typed port is replaced by 8123, or its default (80, 443) is lost to the parser. */
    @Test
    fun aTypedPortIsKept() {
        assertThat(homeAssistantUrl("http://192.168.1.20:80")!!.port).isEqualTo(80)
        assertThat(homeAssistantUrl("https://ha.example.com:443")!!.port).isEqualTo(443)
    }

    /** Fails if an https address with no port isn't given 8123. */
    @Test
    fun httpsWithNoPortGets8123() {
        assertThat(homeAssistantUrl("https://ha.example.com").toString()).isEqualTo("https://ha.example.com:8123/")
    }

    /** Fails if clear text may leave the home network, or any other scheme, path, query or user info is accepted. */
    @Test
    fun everythingElseIsRefused() {
        listOf(
            "http://ha.example.com", "http://8.8.8.8", "ftp://192.168.1.20", "http://192.168.1.20:8123/lovelace",
            "http://192.168.1.20?x=1", "http://192.168.1.20/#x", "http://user@192.168.1.20", "http://192.168.1.20:", "", "   ",
        ).forEach { assertThat(homeAssistantUrl(it)).isNull() }
    }
}
