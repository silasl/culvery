package uk.co.siland.househub.core.access

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

class PinHasher @Inject constructor() {
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun isWellFormed(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun validate(pin: String) = require(isWellFormed(pin)) { "PIN must be $PIN_LENGTH digits" }

    fun newSalt(): String = encoder.encodeToString(ByteArray(16).also(random::nextBytes))

    fun hash(pin: String, salt: String): String = encoder.encodeToString(derive(pin, decoder.decode(salt)))

    fun matches(pin: String, salt: String, hash: String): Boolean =
        MessageDigest.isEqual(derive(pin, decoder.decode(salt)), decoder.decode(hash))

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        const val PIN_LENGTH = 4
        // Kid-proofing, not strong auth: identify() checks every person, so keep this cheap on a 2 GB tablet.
        private const val ITERATIONS = 10_000
    }
}
