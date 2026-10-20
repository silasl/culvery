package uk.co.siland.culvery.capability.lights.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsActions
import uk.co.siland.culvery.capability.lights.LightsFixture
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsHouse
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.TestAccess
import uk.co.siland.culvery.capability.lights.TestLightsProvider
import uk.co.siland.culvery.capability.lights.TestLightsSystem
import uk.co.siland.culvery.capability.lights.link
import uk.co.siland.culvery.core.plugin.test.soon

/** What [LightsTabHost] takes, over a paired fake [system] whose rooms a test changes. */
internal class TabHost(val view: LightsView, val actions: LightsActions, val house: LightsHouse, val follow: LightsFollow, val system: TestLightsSystem)

internal suspend fun LightsFixture.tabHost(vararg rooms: LightRoom, access: TestAccess = TestAccess(null)): TabHost {
    settings.connect("lights.hue", link())
    val provider = TestLightsProvider()
    val bridges = Bridges(setOf(provider), settings, scope)
    soon { bridges.system.first { it != null } }
    val system = provider.systems.single()
    system.rooms.value = rooms.toList()
    val view = LightsView(bridges, settings, scope)
    val lightsHouse = LightsHouse(bridges, settings)
    return TabHost(
        view,
        LightsActions({ bridges.system.value }, scope, System::currentTimeMillis, lightsHouse, access),
        lightsHouse,
        LightsFollow(bridges.system, MutableStateFlow(true), scope),
        system,
    )
}
