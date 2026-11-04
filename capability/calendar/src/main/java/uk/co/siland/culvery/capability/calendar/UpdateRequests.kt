package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** When someone last tapped Update now, so a provider that reads sparingly can tell that tap from the timer's pass. */
@Singleton
class UpdateRequests @Inject constructor() {
    private val latest = MutableStateFlow<Long?>(null)
    val last: StateFlow<Long?> = latest

    fun request(atMillis: Long) {
        latest.value = atMillis
    }
}
