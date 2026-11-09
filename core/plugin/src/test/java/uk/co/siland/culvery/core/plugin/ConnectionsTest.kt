package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConnectionsTest {
    @Test
    fun connectionTextNamesNoAddress() {
        val connection = Connection("c1", "midsussex-bins", "1 Example Road", mapOf("house" to "1", "postcode" to "ZZ1 1ZZ", "line" to "1 Example Road"))

        val text = connection.toString()

        assertThat(text).isEqualTo("Connection(id=c1, provider=midsussex-bins)")
        assertThat(text).doesNotContain("1 Example Road")
        assertThat(text).doesNotContain("ZZ1 1ZZ")
    }
}
