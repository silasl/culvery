package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.house.TodaysBirthdays
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock

private val BIRTHDAY_TITLE = Regex("""\bbirthdays?\b""", RegexOption.IGNORE_CASE)

/**
 * The household members with a birthday on [date] (16 §3.1): a yearly, one-day, all-day event with the word
 * "birthday" in its title, tagged for them. The tag decides, never the calendar the event came from or the title's
 * name, so an untagged or Family event greets nobody. An unreadable series rule counts as yearly; a known other one doesn't.
 */
internal fun birthdaysIn(events: List<StoredEvent>, date: LocalDate, order: List<PersonId>, zone: ZoneId): List<PersonId> {
    return events.filter { it.isYearlyBirthdayOn(date, zone) }
        .flatMap { peopleIn(it.forPerson) }
        .filter { it in order }
        .distinct()
        .sortedBy(order::rankOf)
}

private fun StoredEvent.isYearlyBirthdayOn(date: LocalDate, zone: ZoneId): Boolean {
    val first = start as? EventTime.AllDay ?: return false
    val last = end as? EventTime.AllDay ?: return false
    return daySpan(first, last, zone) == date to date &&
        recurring && (recurrenceRule == null || ruleParts(recurrenceRule)?.get("FREQ") == "YEARLY") &&
        BIRTHDAY_TITLE.containsMatchIn(title)
}

/** Today's birthdays from the stored events of the calendars the wall shows; no pending overlay and no sync health (16 §4.1). */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarBirthdays internal constructor(
    now: StateFlow<LocalDateTime?>,
    events: (fromMillis: Long, toMillis: Long) -> Flow<List<StoredEvent>>,
    people: Flow<List<Person>>,
    zone: Flow<ZoneId>,
    scope: CoroutineScope,
) : TodaysBirthdays {
    @Inject
    constructor(
        clock: HouseholdClock,
        store: CalendarStore,
        household: HouseholdRepository,
        zone: HouseholdZone,
        @ApplicationScope scope: CoroutineScope,
    ) : this(clock.now, store::eventsBetween, household.people, zone.zone, scope)

    override val today: StateFlow<List<Person>> = combine(now.map { it?.toLocalDate() }.distinctUntilChanged(), zone, ::Pair)
        .distinctUntilChanged()
        .flatMapLatest { (date, z) ->
            if (date == null) {
                flowOf(emptyList())
            } else {
                val from = millis(date, z) - ZONE_MARGIN_MS
                val to = millis(date.plusDays(1), z) + ZONE_MARGIN_MS
                combine(events(from, to), people) { stored, household ->
                    val byId = household.associateBy { it.id }
                    birthdaysIn(stored, date, household.map { it.id }, z).mapNotNull(byId::get)
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
}
