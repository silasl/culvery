package uk.co.siland.culvery.core.homeassistant.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.homeassistant.EntityState
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.HomeAssistantLink
import uk.co.siland.culvery.core.homeassistant.homeAssistantUrl

val TEST_LINK = HomeAssistantLink(checkNotNull(homeAssistantUrl("http://192.168.1.20:8123/")), "test-token")

/** A started [HomeAssistant] over [api]; pass the test's `backgroundScope`. */
fun testHomeAssistant(
    api: HomeAssistantApi,
    link: Flow<HomeAssistantLink?> = flowOf(TEST_LINK),
    inFront: StateFlow<Boolean> = MutableStateFlow(true),
    scope: CoroutineScope,
): HomeAssistant = HomeAssistant(api, link, inFront, scope).also { it.start() }

/** An entity as Home Assistant would report it: text and number attributes kept, any other kind dropped (as `HaJson`). */
fun entityState(id: String, state: String, attributes: Map<String, Any> = emptyMap()): EntityState = EntityState(
    entityId = id,
    state = state,
    texts = attributes.filterValues { it is String }.mapValues { it.value as String },
    numbers = attributes.filterValues { it is Number }.mapValues { (it.value as Number).toDouble() },
)
