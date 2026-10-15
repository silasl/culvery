package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 6 §6 privacy: only the home network is contacted. Exactly as written; tidying a typed address is Settings' job. */
class HomeNetworkTest {
    @Test
    fun privateIpv4LiteralsOnly() {
        listOf("10.0.0.5", "172.16.4.2", "172.31.255.1", "192.168.1.20", "169.254.10.1").forEach { assertThat(isHomeNetworkHost(it)).isTrue() }
        listOf(
            "172.32.0.1", "8.8.8.8", "127.0.0.1", "192.168.1", "192.168.1.256", "192.168.001.020", " 192.168.1.20",
            "sonos.local", "localhost", "", "::1", "192.168.1.2O",
        ).forEach { assertThat(isHomeNetworkHost(it)).isFalse() }
    }
}
