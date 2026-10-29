package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.test.soon

@RunWith(AndroidJUnit4::class)
class BridgesTest {
    @get:Rule val lights = LightsFixture()
    private val scope = lights.scope
    private val settings get() = lights.settings
    private val hue = TestLightsProvider()

    // On a real dispatcher: the opening follows DataStore's writes, which the test scheduler doesn't drive.
    private suspend fun Bridges.opensAnother(than: LightsSystem?) = soon { system.first { it != null && it !== than } }

    /** Fails if a system opens before a connection is saved, or the saved one isn't opened on its link. */
    @Test
    fun noConnectionOpensNothingUntilOneIsSaved() = runTest {
        val bridges = Bridges(setOf(hue), settings, scope)
        settings.prefs.first()
        assertThat(bridges.system.value).isNull()
        settings.connect("lights.hue", link())
        bridges.opensAnother(than = null)
        assertThat(hue.opened).containsExactly(link())
    }

    /** Fails if a new address for the same bridge reopens the system instead of being passed to it. */
    @Test
    fun aNewAddressForTheSameBridgeKeepsTheOpenSystem() = runTest {
        settings.connect("lights.hue", link())
        val bridges = Bridges(setOf(hue), settings, scope)
        val first = bridges.opensAnother(than = null)
        settings.connect("lights.hue", link(address = "192.168.1.31"))
        assertThat(soon { hue.systems.single().addresses.first { it.isNotEmpty() } }).containsExactly("192.168.1.31")
        assertThat(bridges.system.value).isSameInstanceAs(first)
        assertThat(hue.opened).containsExactly(link())
    }

    /** Fails if a new key for the same bridge and address keeps the system holding the old key. */
    @Test
    fun aNewKeyForTheSameBridgeReopens() = runTest {
        settings.connect("lights.hue", link())
        val bridges = Bridges(setOf(hue), settings, scope)
        val first = bridges.opensAnother(than = null)
        settings.connect("lights.hue", link(key = "new-key"))
        bridges.opensAnother(than = first)
        assertThat(hue.opened).containsExactly(link(), link(key = "new-key")).inOrder()
    }

    /** Fails if a connection to a provider this build lacks opens something, or blocks a later one that is here. */
    @Test
    fun aConnectionToAProviderNotInTheBuildOpensNothing() = runTest {
        settings.connect("lights.cloud", link())
        val bridges = Bridges(setOf(hue), settings, scope)
        settings.prefs.first { it.providerId == "lights.cloud" }
        assertThat(bridges.system.value).isNull()
        settings.connect("lights.hue", link())
        bridges.opensAnother(than = null)
        assertThat(hue.opened).containsExactly(link())
    }

    /** 13 §8: a debug sample bound ahead of Hue is never what Find Hue Bridge or a typed address asks. */
    @Test
    fun aSampleListedFirstIsNotWhatFindAsks() = runTest {
        val sample = TestLightsProvider("sample.lights", connectable = false)
        val found = BridgeFound("001788fffe00a1b2", "192.168.1.30")
        hue.found = found
        val bridges = Bridges(linkedSetOf(sample, hue), settings, scope)
        assertThat(LightsConnect(bridges, settings).find()).isEqualTo(FindOutcome.Found(found))
        assertThat(sample.asked).isEmpty()
        assertThat(hue.asked).containsExactly("find")
    }
}
