package uk.co.siland.culvery.core.homeassistant

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateExpiredException
import java.util.Base64
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HomeAssistantTrustTest {
    private fun hashOf(name: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(TestCerts.leaf(name).publicKey.encoded))

    /** Calls a MockWebServer on [server]'s keystore, named `localhost`, through [manager]; returns the status or throws. */
    private fun call(server: String, manager: X509TrustManager): Pair<Result<Int>, Int> =
        MockWebServer().use { ha ->
            ha.useHttps(TestCerts.serverSocketFactory(server), false)
            ha.enqueue(MockResponse().setBody("{}"))
            val client = OkHttpClient.Builder()
                .sslSocketFactory(HomeAssistantTrust.socketFactory(manager), manager)
                .hostnameVerifier(HomeAssistantTrust.anyHostname)
                .build()
            val answer = runCatching {
                client.newCall(Request.Builder().url(ha.url("/api/")).build()).execute().use { it.code }
            }
            answer to ha.requestCount
        }

    /** Fails if the pinned key is refused, or a certificate naming another host than the one called is refused. */
    @Test
    fun thePinnedLeafAnswersWhateverItsName() {
        val (answer, requests) = call("bridge", HomeAssistantTrust.pinned(hashOf("bridge")))
        assertThat(answer.getOrThrow()).isEqualTo(200)
        assertThat(requests).isEqualTo(1)
    }

    /** Fails if another key is trusted, or its refusal doesn't carry CertificateChanged. */
    @Test
    fun anotherKeyIsCertificateChanged() {
        val (answer, requests) = call("other", HomeAssistantTrust.pinned(hashOf("bridge")))
        val failure = assertThrows(SSLHandshakeException::class.java) { answer.getOrThrow() }
        assertThat(generateSequence<Throwable>(failure) { it.cause }.any { it is CertificateChanged }).isTrue()
        assertThat(requests).isEqualTo(0)
    }

    /** Fails if the pinned manager checks the certificate's dates, or trusts an expired leaf with another key. */
    @Test
    fun thePinnedLeafIsTrustedPastItsExpiry() {
        val expired = TestCerts.leaf("expired")
        assertThrows(CertificateExpiredException::class.java) { expired.checkValidity() }
        assertThat(call("expired", HomeAssistantTrust.pinned(hashOf("expired"))).first.getOrThrow()).isEqualTo(200)
        assertThrows(CertificateChanged::class.java) {
            HomeAssistantTrust.pinned(hashOf("bridge")).checkServerTrusted(arrayOf(expired), "RSA")
        }
    }

    /** Fails if the capturing manager refuses a certificate or records anything but the leaf's key hash. */
    @Test
    fun capturingRecordsTheLeafsHash() {
        val seen = HomeAssistantTrust.capturing()
        assertThat(seen.pin).isNull()
        assertThat(call("other", seen).first.getOrThrow()).isEqualTo(200)
        assertThat(seen.pin).isEqualTo(hashOf("other"))
    }

    /** Fails if the hash isn't the SHA-256 of the key's SubjectPublicKeyInfo, as openssl prints it. */
    @Test
    fun theHashIsTheKeysSha256() {
        assertThat(fingerprint(pinOf(TestCerts.leaf("bridge"))))
            .isEqualTo("a945 9efc d17e 1641 cdcb bc87 811c c20f dec0 7fe1 d44d 2341 f0ec c139 1d68 d761")
    }

    @Test
    fun theFingerprintIsLowercaseHexInGroupsOfFour() {
        assertThat(fingerprint("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="))
            .isEqualTo("0001 0203 0405 0607 0809 0a0b 0c0d 0e0f 1011 1213 1415 1617 1819 1a1b 1c1d 1e1f")
    }

    /** Fails if anything but the certificate read uses the manager that accepts every certificate. */
    @Test
    fun capturingIsUsedOnlyByTheCertificateRead() {
        // Unit tests run in the module's directory.
        val users = File("src/main/java").walk()
            .filter { it.isFile && it.extension == "kt" && it.name != "HomeAssistantTrust.kt" }
            .filter { "capturing" in it.readText(Charsets.UTF_8) }
            .map { it.name }
            .toList()
        assertThat(users).containsExactly("HomeAssistantClient.kt")
        val client = File("src/main/java/uk/co/siland/culvery/core/homeassistant/HomeAssistantClient.kt").readText(Charsets.UTF_8)
        val start = client.indexOf("override suspend fun certificate(")
        assertThat(start).isAtLeast(0)
        val probe = client.substring(start, Regex("\\R    }\\R").find(client, start)!!.range.first)
        assertThat(Regex("capturing").findAll(client).count()).isEqualTo(1)
        assertThat(probe).contains("capturing")
    }
}
