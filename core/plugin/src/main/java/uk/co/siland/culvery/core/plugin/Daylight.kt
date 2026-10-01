package uk.co.siland.culvery.core.plugin

import kotlinx.coroutines.flow.Flow

/** Today's sunrise and sunset in the household's zone, or null when unknown (4b design §3.2). `:app` takes it as optional. */
interface Daylight {
    val today: Flow<SunTimes?>
}
