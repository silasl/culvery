package uk.co.siland.culvery.provider.lights_hue

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

/** The keystores in `src/test/resources/tls`: `bridge` and `other` on the test CA, `rogue` on another CA. */
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

    fun testCa(): X509Certificate =
        resource("tls/ca.pem").use { CertificateFactory.getInstance("X.509").generateCertificate(it) } as X509Certificate

    private fun resource(path: String) = checkNotNull(TestCerts::class.java.classLoader).getResourceAsStream(path)
        ?: error("missing test resource $path")
}
