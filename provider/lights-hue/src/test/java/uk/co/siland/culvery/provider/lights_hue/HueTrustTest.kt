package uk.co.siland.culvery.provider.lights_hue

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HueTrustTest {
    private val trust = HueTrust(TestCerts.testCa())

    /** Calls a MockWebServer on [server]'s keystore through [tls]; returns the status, or throws, and the request count. */
    private fun call(server: String, tls: Pair<SSLSocketFactory, X509TrustManager>): Pair<Result<Int>, Int> =
        MockWebServer().use { bridge ->
            bridge.useHttps(TestCerts.serverSocketFactory(server), false)
            bridge.enqueue(MockResponse().setBody(HueFixtures.CONFIG))
            val client = OkHttpClient.Builder()
                .sslSocketFactory(tls.first, tls.second)
                .hostnameVerifier(trust.hostnames)
                .build()
            val answer = runCatching {
                client.newCall(Request.Builder().url(bridge.url("/api/0/config")).build()).execute().use { it.code }
            }
            answer to bridge.requestCount
        }

    private fun Result<Int>.handshakeFailure(): SSLHandshakeException =
        assertThrows(SSLHandshakeException::class.java) { getOrThrow() }

    private fun Throwable.causes(): List<Throwable> = generateSequence(this) { it.cause }.toList()

    /** Fails if a bridge on the Hue CA, naming the id asked for, is refused (7 §3.2). */
    @Test
    fun theBridgeOnTheHueCaWithItsIdAnswers() {
        val (answer, requests) = call("bridge", trust.forBridge("001788fffe00a1b2"))
        assertThat(answer.getOrThrow()).isEqualTo(200)
        assertThat(requests).isEqualTo(1)
    }

    /** Fails if a certificate from another CA is trusted because its name is the bridge's id. */
    @Test
    fun anotherCasCertificateIsRefused() {
        val (answer, requests) = call("rogue", trust.forBridge("001788fffe00a1b2"))
        answer.handshakeFailure()
        assertThat(requests).isEqualTo(0)
    }

    /** Fails if the name isn't checked, or a wrong name doesn't surface as WrongBridgeCertificate. */
    @Test
    fun aCertificateNamingAnotherBridgeIsRefused() {
        val (answer, requests) = call("other", trust.forBridge("001788fffe00a1b2"))
        assertThat(answer.handshakeFailure().causes().any { it is WrongBridgeCertificate }).isTrue()
        assertThat(requests).isEqualTo(0)
    }

    /** Fails if the id is compared with case, so an upper-case id from mDNS or config is refused. */
    @Test
    fun theNameIsComparedWithoutCase() {
        val (answer, _) = call("bridge", trust.forBridge("001788FFFE00A1B2"))
        assertThat(answer.getOrThrow()).isEqualTo(200)
    }

    /** Fails if the chain-only route checks a name, or stops checking the chain. */
    @Test
    fun anyBridgeChecksOnlyTheChain() {
        assertThat(call("bridge", trust.forAnyBridge()).first.getOrThrow()).isEqualTo(200)
        assertThat(call("other", trust.forAnyBridge()).first.getOrThrow()).isEqualTo(200)
        val (rogue, requests) = call("rogue", trust.forAnyBridge())
        rogue.handshakeFailure()
        assertThat(requests).isEqualTo(0)
    }

    /** Fails if the common name is read from the wrong attribute, or a certificate with none gives a name. */
    @Test
    fun theCommonNameIsTheLeafsCn() {
        assertThat(commonName(TestCerts.leaf("other"))).isEqualTo("001788fffe00c3d4")
        assertThat(commonName(TestCerts.testCa())).isEqualTo("root-bridge")
    }

    /** Fails if the bundled file is missing, isn’t exactly Hue’s two self-signed roots, or loses one (7 §3.2). */
    @Test
    fun theBundledCaIsHuesRoot() {
        val roots = bundledRoots()
        roots.forEach { root ->
            root.verify(root.publicKey)
            assertThat(root.basicConstraints).isAtLeast(0)
        }
        assertThat(roots.map { it.subjectX500Principal.name })
            .containsExactly("CN=root-bridge,O=Philips Hue,C=NL", "CN=Hue Root CA 01,O=Signify Hue,C=NL")
    }
}
