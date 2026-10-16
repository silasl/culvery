package uk.co.siland.culvery.provider.lights_hue

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/** The bridge's certificate is on a Hue root but names another bridge. */
internal class WrongBridgeCertificate : CertificateException("The certificate names another bridge")

/** TLS to a Hue Bridge: the chain must reach a bundled Hue root, and a paired bridge's certificate must carry its id (7 §3.2). */
internal class HueTrust(roots: () -> List<X509Certificate> = ::bundledRoots) {
    constructor(ca: X509Certificate) : this({ listOf(ca) })

    // Read at the first connection, not when Hilt builds the provider.
    private val roots by lazy(roots)

    /** Each client is built for one bridge, so its trust manager checks the name and the host name is not used. */
    val hostnames = HostnameVerifier { _, _ -> true }

    fun forBridge(bridgeId: String): Pair<SSLSocketFactory, X509TrustManager> =
        tls(NamedBridge(chainOnly(), bridgeId))

    /** For `/api/config` before the bridge's id is known. */
    fun forAnyBridge(): Pair<SSLSocketFactory, X509TrustManager> = tls(chainOnly())

    private fun chainOnly(): X509TrustManager {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            roots.forEachIndexed { i, root -> setCertificateEntry("hue-root-$i", root) }
        }
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().single()
    }

    private fun tls(manager: X509TrustManager): Pair<SSLSocketFactory, X509TrustManager> =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }.socketFactory to manager

    private class NamedBridge(private val chain: X509TrustManager, private val bridgeId: String) :
        X509TrustManager by chain {
        override fun checkServerTrusted(certificates: Array<out X509Certificate>, authType: String) {
            chain.checkServerTrusted(certificates, authType)
            if (!commonName(certificates[0]).equals(bridgeId, ignoreCase = true)) throw WrongBridgeCertificate()
        }
    }
}

internal fun bundledRoots(): List<X509Certificate> {
    val pem = checkNotNull(HueTrust::class.java.classLoader).getResourceAsStream("hue/hue_root_ca.pem")
        ?: error("hue/hue_root_ca.pem is missing")
    return pem.use { CertificateFactory.getInstance("X.509").generateCertificates(it) }
        .map { it as X509Certificate }
}

private val UNESCAPED_SEPARATOR = Regex("""(?<!\\)[,+]""")
private val ESCAPED = Regex("""\\(.)""")

/** The subject's CN, or null when it has none; RFC 2253 escapes commas and plus signs inside values. */
internal fun commonName(cert: X509Certificate): String? =
    cert.subjectX500Principal.getName(X500Principal.RFC2253)
        .split(UNESCAPED_SEPARATOR)
        .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
        ?.substring(3)
        ?.replace(ESCAPED, "$1")
