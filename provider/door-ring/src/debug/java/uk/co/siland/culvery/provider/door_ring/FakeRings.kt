package uk.co.siland.culvery.provider.door_ring

/**
 * Debug builds (5 §7): the adb shell's notifications tagged [FAKE_RING_TAG] read as Ring's, and the Ring app counts as
 * installed, since the emulator has none (5 §8.1).
 */
object FakeRings {
    const val ENABLED = true
}
