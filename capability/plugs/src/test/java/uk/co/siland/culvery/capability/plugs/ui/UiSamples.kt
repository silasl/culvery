package uk.co.siland.culvery.capability.plugs.ui

import uk.co.siland.culvery.capability.plugs.DESK
import uk.co.siland.culvery.capability.plugs.FAN
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugState

internal const val FAIRY = "switch.fairy_lights"

internal val LAMP_ON = PlugState(LAMP, "Lamp", PlugPower.ON, 182)
internal val LAMP_OFF = PlugState(LAMP, "Lamp", PlugPower.OFF, null)
internal val FAN_OFF = PlugState(FAN, "Fan", PlugPower.OFF, null)
internal val FAIRY_LOST = PlugState(FAIRY, "Fairy lights", PlugPower.UNREACHABLE, null)
internal val DESK_OFF = PlugState(DESK, "Desk", PlugPower.OFF, null)
