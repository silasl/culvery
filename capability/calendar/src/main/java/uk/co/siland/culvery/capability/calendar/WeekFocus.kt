package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The week the Calendar tab should show next, as weeks after this one (9 §3.1 After Add). The editor lives in the
 * shell's overlay, not the tab, so it asks here; the week view shows it and says it has.
 */
@Singleton
class WeekFocus @Inject constructor() {
    private val asked = MutableStateFlow<Int?>(null)

    val requested: StateFlow<Int?> = asked.asStateFlow()

    fun show(weeksAhead: Int) {
        asked.value = weeksAhead
    }

    fun consumed() {
        asked.value = null
    }
}
