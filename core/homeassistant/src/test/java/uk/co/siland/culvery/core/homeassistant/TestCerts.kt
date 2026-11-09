package uk.co.siland.culvery.core.homeassistant

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

/** The keystores in `src/test/resources/tls`: `bridge` and `other`, copied from the Hue tests, hold different keys; `expired`'s certificate ended on 2 January 2000. */
internal object TestCerts {
    private const val PASSWORD = "culvery-test"

    private fun store(name: String): KeyStore =
        KeyStore.getInstance("PKCS12").apply {
            resource("tls/$name.p12").use { load(it, PASSWORD.toCharArray()) }
        }

    fun serverSocketFactory(name: String): SSLSocketFactory {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(store(name), PASSWORD.toCharArray()) }
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }.socketFactory
    }

    fun leaf(name: String): X509Certificate = store(name).getCertificate(name) as X509Certificate

    private fun resource(path: String) = checkNotNull(TestCerts::class.java.classLoader).getResourceAsStream(path)
        ?: error("missing test resource $path")
}
