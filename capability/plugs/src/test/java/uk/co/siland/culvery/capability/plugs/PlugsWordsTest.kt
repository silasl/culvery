package uk.co.siland.culvery.capability.plugs

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.house.PlugChange

class PlugsWordsTest {
    private fun plug(power: PlugPower, watts: Int? = null) = PlugState("switch.lamp", "Lamp", power, watts)

    /** 12 §2.5: fails if a status line differs from the Copy. */
    @Test
    fun statuses() {
        assertThat(PlugsWords.plugStatus(plug(PlugPower.ON, 182))).isEqualTo("On · 182 W")
        assertThat(PlugsWords.plugStatus(plug(PlugPower.ON))).isEqualTo("On")
        assertThat(PlugsWords.plugStatus(plug(PlugPower.OFF, 182))).isEqualTo("Off")
        assertThat(PlugsWords.plugStatus(plug(PlugPower.UNREACHABLE))).isEqualTo("Can’t reach this plug.")
        assertThat(PlugsWords.plugStatus(plug(PlugPower.GONE)))
            .isEqualTo("Home Assistant has no plug called that any more. Turn it off in Settings.")
        assertThat(PlugsWords.plugStatus(plug(PlugPower.UNKNOWN))).isEqualTo("Checking…")
        assertThat(PlugsWords.plugStatus(plug(PlugPower.NO_LINK))).isEqualTo("Can’t reach Home Assistant.")
        assertThat(PlugsWords.CANT_REACH_HA).isEqualTo("Can’t reach Home Assistant.")
    }

    /** 12 §2.5: fails if what TalkBack reads for a card differs. */
    @Test
    fun spoken() {
        assertThat(PlugsWords.plugSpoken(plug(PlugPower.ON, 182))).isEqualTo("Plug Lamp, on, 182 watts.")
        assertThat(PlugsWords.plugSpoken(plug(PlugPower.OFF))).isEqualTo("Plug Lamp, off.")
        assertThat(PlugsWords.plugSpoken(plug(PlugPower.UNREACHABLE))).isEqualTo("Plug Lamp, can’t reach this plug.")
        assertThat(PlugsWords.plugSpoken(plug(PlugPower.UNKNOWN))).isEqualTo("Plug Lamp, checking.")
        assertThat(PlugsWords.plugSpoken(plug(PlugPower.GONE)))
            .isEqualTo("Plug Lamp. Home Assistant has no plug called that any more. Turn it off in Settings.")
        assertThat(PlugsWords.plugSpoken(plug(PlugPower.NO_LINK))).isEqualTo("Plug Lamp, can’t reach Home Assistant.")
        assertThat(PlugsWords.switchSpoken(plug(PlugPower.ON), needsPin = false)).isEqualTo("Switch off")
        assertThat(PlugsWords.switchSpoken(plug(PlugPower.OFF), needsPin = true)).isEqualTo("Switch on, needs a PIN")
    }

    @Test
    fun modeRowsAreSpokenWithTheirChoice() {
        assertThat(PlugsWords.modeRowSpoken("Holiday mode", "Lamp", ModeChoice.IGNORE)).isEqualTo("Holiday mode, Lamp, ignore")
        assertThat(PlugsWords.modeRowSpoken("Holiday mode", "Lamp", ModeChoice.ON)).isEqualTo("Holiday mode, Lamp, on")
        assertThat(PlugsWords.modeRowSpoken("Holiday mode", "Lamp", ModeChoice.OFF)).isEqualTo("Holiday mode, Lamp, off")
    }

    @Test
    fun theColumnCellIsSpokenWithItsMode() {
        assertThat(PlugsWords.modeCellSpoken("Holiday mode", ModeChoice.IGNORE)).isEqualTo("Holiday mode, ignore")
        assertThat(PlugsWords.modeCellSpoken("Holiday mode", ModeChoice.ON)).isEqualTo("Holiday mode, on")
        assertThat(PlugsWords.modeCellSpoken("Holiday mode", ModeChoice.OFF)).isEqualTo("Holiday mode, off")
    }

    @Test
    fun theModeChoicesAreIgnoreOnOff() {
        assertThat(listOf(PlugsWords.IGNORE, PlugsWords.ON, PlugsWords.OFF)).containsExactly("Ignore", "On", "Off").inOrder()
    }

    @Test
    fun theHeaderCountsThePlugsThatAreOn() {
        val plugs = listOf(plug(PlugPower.ON), plug(PlugPower.ON), plug(PlugPower.OFF), plug(PlugPower.UNREACHABLE))
        assertThat(PlugsWords.plugsOn(plugs)).isEqualTo("2 of 3 on · Home Assistant")
    }

    @Test
    fun theHeaderCountsOnlyPlugsWhoseStateIsKnown() {
        assertThat(PlugsWords.plugsOn(listOf(plug(PlugPower.ON), plug(PlugPower.OFF), plug(PlugPower.UNKNOWN))))
            .isEqualTo("1 of 2 on · Home Assistant")
        assertThat(PlugsWords.plugsOn(listOf(plug(PlugPower.UNKNOWN), plug(PlugPower.UNKNOWN)))).isEqualTo("Home Assistant")
        assertThat(PlugsWords.plugsOn(emptyList())).isEqualTo("Home Assistant")
    }

    @Test
    fun theSettingsSectionWords() {
        assertThat(PlugsWords.PLUGS_HELP)
            .isEqualTo("Plugs from Home Assistant’s TP-Link integration. Turn one on to show it on the Plugs tab.")
        assertThat(PlugsWords.NO_TP_LINK_PLUGS).isEqualTo("Home Assistant has no TP-Link plugs. Add them in Home Assistant first.")
        assertThat(PlugsWords.CANT_LIST_PLUGS).isEqualTo("Can’t list the plugs. Check Home Assistant is up to date.")
        assertThat(PlugsWords.SHOW_ON_TAB).isEqualTo("Show on the Plugs tab")
        assertThat(PlugsWords.NEEDS_A_PIN).isEqualTo("Needs a PIN to switch")
        assertThat(PlugsWords.GONE_SHOWN).isEqualTo("Home Assistant has no plug called that any more.")
        assertThat(PlugsWords.showSpoken("Lamp")).isEqualTo("Lamp, show on the Plugs tab")
        assertThat(PlugsWords.needsPinSpoken("Lamp")).isEqualTo("Lamp, needs a PIN to switch")
    }

    @Test
    fun theToastsNameThePlug() {
        assertThat(PlugsWords.cantReach("Lamp")).isEqualTo("Can’t reach Lamp. Check it’s plugged in and the Tapo app can see it.")
        assertThat(PlugsWords.didntSwitch("Lamp")).isEqualTo("Lamp didn’t switch. Try again, or use the Tapo app.")
    }

    @Test
    fun theTileLinesSayHowManyDidNotSwitch() {
        assertThat(PlugsWords.enterLine(listOf(PlugChange("Lamp", false)))).isEqualTo("Lamp didn’t switch off. It’ll keep trying.")
        assertThat(PlugsWords.enterLine(listOf(PlugChange("Lamp", false), PlugChange("Fan", true))))
            .isEqualTo("2 plugs didn’t switch. They’ll keep trying.")
        assertThat(PlugsWords.leaveLine(listOf(PlugChange("Lamp", true))))
            .isEqualTo("Lamp didn’t switch back on. Switch it on the Plugs tab.")
        assertThat(PlugsWords.leaveLine(listOf(PlugChange("Lamp", true), PlugChange("Fan", false))))
            .isEqualTo("2 plugs didn’t switch back. Switch them on the Plugs tab.")
    }

    @Test
    fun aPlugThatShouldHaveGoneBackOffSaysSo() {
        assertThat(PlugsWords.leaveLine(listOf(PlugChange("Fan", false))))
            .isEqualTo("Fan didn’t switch back off. Switch it off on the Plugs tab.")
    }

    @Test
    fun aPlugWithoutANameIsCalledAPlug() {
        assertThat(PlugsWords.A_PLUG).isEqualTo("A plug")
    }
}
