package uk.co.siland.culvery.core.plugin

import kotlinx.coroutines.flow.StateFlow

/**
 * True while Culvery is resumed in front and the screen is awake (19 §3.1); false on the dark screen, the night clock,
 * Holiday's dark screen and whenever Culvery isn't in front. `AlertHost.inFront` keeps its meaning.
 */
interface ScreenLit {
    val lit: StateFlow<Boolean>
}
