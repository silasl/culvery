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
class LightsSettingsTest {
    @get:Rule val lights = LightsFixture()
    private val settings get() = lights.settings

    /** Fails if an unpaired home starts with a bridge, a main room, Holiday mode on, or anything left out. */
    @Test
    fun theDefaults() = runTest {
        val prefs = soon { settings.prefs.first() }
        assertThat(prefs).isEqualTo(LightsPrefs())
        assertThat(prefs.link).isNull()
        assertThat(prefs.holidayOn).isFalse()
        assertThat(prefs.leftOut).isEmpty()
    }

    /** Fails if a setter doesn't save what it is given, or a saved bridge doesn't come back as one link. */
    @Test
    fun aConnectionIsKeptWithItsChoices() = runTest {
        soon {
            settings.connect("lights.hue", link())
            settings.setMainRoom("Lounge-id")
            settings.setLeftOut(setOf("Garden-id"))
            settings.setHolidayAutomations(setOf("mimic-id"))
            settings.setHolidayOn(true)
            settings.setHolidayStarted(setOf("mimic-id"))
        }
        val prefs = soon { settings.prefs.first { it.holidayStarted.isNotEmpty() } }
        assertThat(prefs).isEqualTo(
            LightsPrefs("lights.hue", "001788fffe00a1b2", "192.168.1.30", "test-key", "Lounge-id", setOf("Garden-id"), setOf("mimic-id"), true, setOf("mimic-id")),
        )
        assertThat(prefs.link).isEqualTo(link())
        soon { settings.setMainRoom(null) }
        assertThat(soon { settings.prefs.first { it.mainRoomId == null } }.link).isEqualTo(link())
    }

    /** Fails if choices for one bridge survive pairing with another, or are lost when only the address changes. */
    @Test
    fun anotherBridgeForgetsTheChoices() = runTest {
        soon {
            settings.connect("lights.hue", link())
            settings.setMainRoom("Lounge-id")
            settings.setLeftOut(setOf("Garden-id"))
            settings.setHolidayAutomations(setOf("mimic-id"))
            settings.setHolidayOn(true)
            settings.setHolidayStarted(setOf("mimic-id"))
            settings.connect("lights.hue", link(address = "192.168.1.31"))
        }
        val same = soon { settings.prefs.first { it.address == "192.168.1.31" } }
        assertThat(same.mainRoomId).isEqualTo("Lounge-id")
        assertThat(same.leftOut).containsExactly("Garden-id")
        assertThat(same.holidayAutomations).containsExactly("mimic-id")
        assertThat(same.holidayOn).isTrue()
        assertThat(same.holidayStarted).containsExactly("mimic-id")

        soon { settings.connect("lights.hue", link(bridgeId = "001788fffe00ffff")) }
        val other = soon { settings.prefs.first { it.bridgeId == "001788fffe00ffff" } }
        assertThat(other.mainRoomId).isNull()
        assertThat(other.leftOut).isEmpty()
        assertThat(other.holidayAutomations).isEmpty()
        assertThat(other.holidayStarted).isEmpty()
        assertThat(other.holidayOn).isFalse()
    }

    /** Fails if a moved bridge's address is dropped, or another bridge's address overwrites the saved one. */
    @Test
    fun movedChangesOnlyTheSameBridgesAddress() = runTest {
        soon { settings.connect("lights.hue", link()) }
        soon { settings.moved(link(address = "192.168.1.40")) }
        assertThat(soon { settings.prefs.first { it.address == "192.168.1.40" } }.link).isEqualTo(link(address = "192.168.1.40"))
        soon { settings.moved(link(bridgeId = "001788fffe00ffff", address = "192.168.1.99")) }
        soon { settings.setMainRoom("Lounge-id") }
        assertThat(soon { settings.prefs.first { it.mainRoomId == "Lounge-id" } }.address).isEqualTo("192.168.1.40")
    }

    /** Fails if the application key, the bridge's address, or a bridge, room or automation id can be read out of a log line. */
    @Test
    fun theKeyIsNeverInToString() {
        val secret = "secret-key-value"
        val linked = link(key = secret).toString()
        assertThat(linked).doesNotContain(secret)
        assertThat(linked).doesNotContain("192.168.1.30")
        assertThat(linked).doesNotContain("001788fffe00a1b2")
        val prefs = LightsPrefs(
            bridgeId = "001788fffe00a1b2", address = "192.168.1.30", key = secret, mainRoomId = "Lounge-id",
            leftOut = setOf("Garden-id"), holidayAutomations = setOf("mimic-id"), holidayStarted = setOf("away-id"),
        ).toString()
        listOf(secret, "192.168.1.30", "001788fffe00a1b2", "Lounge-id", "Garden-id", "mimic-id", "away-id").forEach {
            assertThat(prefs).doesNotContain(it)
        }
    }
}
