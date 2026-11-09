package uk.co.siland.culvery.core.homeassistant

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** Home Assistant's certificate holds a key other than the remembered one. */
internal class CertificateChanged : CertificateException("The certificate's key is not the remembered one")

/** TLS to Home Assistant: a linked one must hold the remembered key (its pin); its name, chain and dates aren't checked. */
internal object HomeAssistantTrust {
    /** Ignores the host name, so it is safe only with [pinned], where the key alone says which server this is, or for [capturing]'s tokenless read. */
    val anyHostname = HostnameVerifier { _, _ -> true }

    /** Trusts only a leaf whose key hashes to [pin]; anything else is [CertificateChanged]. */
    fun pinned(pin: String): X509TrustManager = Pinned(pin)

    /** Trusts any certificate and records the leaf's pin. Only for the read that sends no token. */
    fun capturing(): Capturing = Capturing()

    /** A socket factory with its own TLS context, so no session is shared with another manager's. */
    fun socketFactory(manager: X509TrustManager): SSLSocketFactory =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }.socketFactory

    private class Pinned(pin: String) : ServerOnly() {
        private val expected: ByteArray? = try {
            Base64.getDecoder().decode(pin)
        } catch (_: IllegalArgumentException) {
            null
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("No certificate")
            if (expected == null || !MessageDigest.isEqual(sha256(leaf), expected)) throw CertificateChanged()
        }
    }

    class Capturing internal constructor() : ServerOnly() {
        /** The pin of the last leaf seen; null before the handshake. */
        @Volatile
        var pin: String? = null
            private set

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            pin = pinOf(chain.firstOrNull() ?: throw CertificateException("No certificate"))
        }
    }

    abstract class ServerOnly internal constructor() : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            throw CertificateException("Culvery is never the server")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

private fun sha256(cert: X509Certificate): ByteArray = MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)

/** Base64 of the SHA-256 of [cert]'s SubjectPublicKeyInfo, as OkHttp's `CertificatePinner` hashes it. */
internal fun pinOf(cert: X509Certificate): String = Base64.getEncoder().encodeToString(sha256(cert))

/** [pin]'s SHA-256 as lowercase hex in 16 groups of four, joined by single spaces, for a person to compare. */
fun fingerprint(pin: String): String =
    Base64.getDecoder().decode(pin).joinToString("") { "%02x".format(it) }.chunked(4).joinToString(" ")
