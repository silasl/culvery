package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicSettingsTest {
    @get:Rule val music = MusicFixture()
    private val settings get() = music.settings
    private val home = MusicLink("Sonos_home", "192.168.1.20:1400")

    /** 6 §2.3, §5.4: not connected, no usual room, 20 %. */
    @Test
    fun theDefaults() = runTest {
        assertThat(settings.prefs.first()).isEqualTo(MusicPrefs(null, null, null, null, 20))
        assertThat(settings.prefs.first().link).isNull()
    }

    @Test
    fun aConnectionIsKeptWithItsUsualRoomAndShare() = runTest {
        settings.connect("music.sonos", home)
        settings.setUsualRoom("RINCON_A")
        settings.setLowerTo(35)
        val expected = MusicPrefs("music.sonos", "Sonos_home", "192.168.1.20:1400", "RINCON_A", 35)
        assertThat(settings.prefs.first()).isEqualTo(expected)
        assertThat(settings.prefs.first().link).isEqualTo(home)
        settings.current.first { it.lowerTo == 35 }
        assertThat(settings.current.value).isEqualTo(expected)
    }

    /** Another household's rooms aren't this one's. Fails if Find Sonos on a new system keeps the old usual room. */
    @Test
    fun anotherHouseholdForgetsTheUsualRoom() = runTest {
        settings.connect("music.sonos", home)
        settings.setUsualRoom("RINCON_A")
        settings.connect("music.sonos", home.copy(address = "192.168.1.30:1400"))
        assertThat(settings.prefs.first().usualRoomId).isEqualTo("RINCON_A")
        settings.connect("music.sonos", MusicLink("Sonos_other", "192.168.1.40:1400"))
        assertThat(settings.prefs.first().usualRoomId).isNull()
    }

    /** 6 §4.1: a rescan saves the new address of the same household only. */
    @Test
    fun movedChangesOnlyTheSameHouseholdsAddress() = runTest {
        settings.connect("music.sonos", home)
        settings.moved(MusicLink("Sonos_other", "192.168.1.99:1400"))
        assertThat(settings.prefs.first().address).isEqualTo("192.168.1.20:1400")
        settings.moved(home.copy(address = "192.168.1.21:1400"))
        assertThat(settings.prefs.first().link).isEqualTo(home.copy(address = "192.168.1.21:1400"))
    }

    /** 6 §5.4: 0–50 %. */
    @Test
    fun theShareStaysBetweenNothingAndHalf() = runTest {
        settings.setLowerTo(80)
        assertThat(settings.prefs.first().lowerTo).isEqualTo(50)
        settings.setLowerTo(-5)
        assertThat(settings.prefs.first().lowerTo).isEqualTo(0)
    }
}
