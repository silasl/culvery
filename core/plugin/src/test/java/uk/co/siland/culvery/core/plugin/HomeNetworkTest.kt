package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §6 privacy: only the home network is contacted. */
class HomeNetworkTest {
    /** Fails if a public, loopback, short, out-of-range, zero-padded, spaced or named host counts as the home network. */
    @Test
    fun privateIpv4LiteralsOnly() {
        listOf("10.0.0.5", "172.16.4.2", "172.31.255.1", "192.168.1.20", "169.254.10.1").forEach { assertThat(isHomeNetworkHost(it)).isTrue() }
        listOf(
            "172.32.0.1", "8.8.8.8", "127.0.0.1", "192.168.1", "192.168.1.256", "192.168.001.020", " 192.168.1.20",
            "sonos.local", "localhost", "", "::1", "192.168.1.2O",
        ).forEach { assertThat(isHomeNetworkHost(it)).isFalse() }
    }

    /** Fails if a port, a name, a public address or a fifth number gets through, or spaces and leading zeros don't tidy. */
    @Test
    fun aTypedAddressIsTidiedThenChecked() {
        assertThat(homeNetworkHost("192.168.1.20")).isEqualTo("192.168.1.20")
        assertThat(homeNetworkHost(" 192.168.1.20 ")).isEqualTo("192.168.1.20")
        assertThat(homeNetworkHost("192.168.001.020")).isEqualTo("192.168.1.20")
        assertThat(homeNetworkHost("10.0.0.5")).isEqualTo("10.0.0.5")
        assertThat(homeNetworkHost("172.16.4.2")).isEqualTo("172.16.4.2")
        assertThat(homeNetworkHost("172.31.255.1")).isEqualTo("172.31.255.1")
        assertThat(homeNetworkHost("169.254.10.1")).isEqualTo("169.254.10.1")
        listOf(
            "172.32.0.1", "8.8.8.8", "192.168.1", "192.168.1.20.1", "192.168.1.256", "192.168.1.20:1400", "sonos.local",
            "", "192.168.1.2O", "１９２.168.1.20", "192.168..20", "192.168.1.0020",
        ).forEach { assertThat(homeNetworkHost(it)).isNull() }
    }

    /** Fails if a missing port isn't defaulted only when a default is given, or a bad port, an empty host or an IPv6 literal gets through. */
    @Test
    fun anAuthoritySplitsIntoHostAndPort() {
        assertThat(splitAuthority("192.168.1.20:1400", defaultPort = null)).isEqualTo("192.168.1.20" to 1400)
        assertThat(splitAuthority("192.168.1.30", defaultPort = 443)).isEqualTo("192.168.1.30" to 443)
        assertThat(splitAuthority("192.168.1.30:8443", defaultPort = 443)).isEqualTo("192.168.1.30" to 8443)
        assertThat(splitAuthority("192.168.1.20", defaultPort = null)).isNull()
        listOf(":1400", "192.168.1.20:port", "::1:443", "192.168.1.20:").forEach { assertThat(splitAuthority(it, defaultPort = 443)).isNull() }
    }
}
