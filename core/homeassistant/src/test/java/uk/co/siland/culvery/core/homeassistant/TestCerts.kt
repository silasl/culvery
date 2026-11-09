package uk.co.siland.culvery.core.homeassistant

import java.math.BigInteger
import java.security.KeyStore
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateExpiredException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

/** The keystores in `src/test/resources/tls`, copied from the Hue tests: `bridge` and `other` hold different keys. */
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

    /** [cert] as one whose validity ended in 2000. */
    fun expired(cert: X509Certificate): X509Certificate = Expired(cert)

    private fun resource(path: String) = checkNotNull(TestCerts::class.java.classLoader).getResourceAsStream(path)
        ?: error("missing test resource $path")

    private class Expired(private val cert: X509Certificate) : X509Certificate() {
        private val ended = Date(946_684_800_000L)

        override fun checkValidity() = throw CertificateExpiredException("expired")
        override fun checkValidity(date: Date?) = throw CertificateExpiredException("expired")
        override fun getNotAfter(): Date = ended
        override fun getNotBefore(): Date = cert.notBefore
        override fun getVersion(): Int = cert.version
        override fun getSerialNumber(): BigInteger = cert.serialNumber
        // Abstract in X509Certificate; answered with the non-deprecated principals.
        override fun getIssuerDN(): Principal = cert.issuerX500Principal
        override fun getSubjectDN(): Principal = cert.subjectX500Principal
        override fun getTBSCertificate(): ByteArray = cert.tbsCertificate
        override fun getSignature(): ByteArray = cert.signature
        override fun getSigAlgName(): String = cert.sigAlgName
        override fun getSigAlgOID(): String = cert.sigAlgOID
        override fun getSigAlgParams(): ByteArray? = cert.sigAlgParams
        override fun getIssuerUniqueID(): BooleanArray? = cert.issuerUniqueID
        override fun getSubjectUniqueID(): BooleanArray? = cert.subjectUniqueID
        override fun getKeyUsage(): BooleanArray? = cert.keyUsage
        override fun getBasicConstraints(): Int = cert.basicConstraints
        override fun getEncoded(): ByteArray = cert.encoded
        override fun verify(key: PublicKey?) = cert.verify(key)
        override fun verify(key: PublicKey?, sigProvider: String?) = cert.verify(key, sigProvider)
        override fun toString(): String = "expired test certificate"
        override fun getPublicKey(): PublicKey = cert.publicKey
        override fun hasUnsupportedCriticalExtension(): Boolean = cert.hasUnsupportedCriticalExtension()
        override fun getCriticalExtensionOIDs(): MutableSet<String>? = cert.criticalExtensionOIDs
        override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = cert.nonCriticalExtensionOIDs
        override fun getExtensionValue(oid: String?): ByteArray? = cert.getExtensionValue(oid)
    }
}
