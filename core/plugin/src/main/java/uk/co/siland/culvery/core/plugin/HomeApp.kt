package uk.co.siland.culvery.core.plugin

import kotlinx.coroutines.flow.StateFlow

/** Whether Culvery is the tablet's default home app (4c design D3, §5.1); `:app` checks again each time Culvery is in front. */
interface HomeApp {
    val isDefault: StateFlow<Boolean>
}
