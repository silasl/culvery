package uk.co.siland.culvery.provider.calendar_google

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar_testkit.CalendarProviderContractTest
import uk.co.siland.culvery.core.plugin.Connection

/** The shared contract, against Google through the fake server (3a design D15). */
@RunWith(RobolectricTestRunner::class)
class GoogleCalendarProviderContractTest : CalendarProviderContractTest() {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val google = FakeGoogleServer(london)
    private val family = CalendarSource("family@example.com", "family@example.com", writable = true, primary = true)
    private val subject = GoogleCalendarProvider(GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()), FakeAuthorizer(), NoToasts)

    init {
        google.addCalendar(family.id, family.name, primary = true)
        google.addCalendar("school@group", "School terms", accessRole = "reader")
        fun at(day: Long, hour: Int) = today.plusDays(day).atTime(hour, 0).atZone(london).toInstant()
        google.putEvent(family.id, google.timed("swim", "Swim", at(0, 16), at(0, 17)))
        google.putEvent(family.id, google.allDay("bins", "Bin day", today.plusDays(2), today.plusDays(3)))
        google.putEvent(family.id, google.timed("piano", "Piano", at(1, 15), at(1, 16)) { putJsonArray("recurrence") { add("RRULE:FREQ=WEEKLY") } })
        google.putEvent(family.id, google.timed("piano_1", "Piano", at(1, 15), at(1, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("piano_2", "Piano", at(8, 15), at(8, 16)) { put("recurringEventId", "piano") })
        google.putEvent(family.id, google.timed("trip", "School trip", at(20, 8), at(20, 15)))
        google.putEvent("school@group", google.allDay("inset", "INSET day", today.plusDays(3), today.plusDays(4)))
    }

    @After
    fun tearDown() {
        google.shutdown()
        assertLogsHoldNoPersonalData("School terms")
    }

    override fun provider() = subject
    override fun connection() = Connection("g1", GOOGLE_PROVIDER_ID, "Google", mapOf(CONFIG_ACCOUNT to family.id))
    override fun range() = DateRange(today.minusDays(1), today.plusDays(15), london)
    override fun sourceWithEvents() = family
    override fun outOfRangeEventTitle() = "School trip"
    override fun recurringTitle() = "Piano"
    override fun simulateAuthFailure() = {
        google.failNext(401, "authError")
        google.failNext(401, "authError")
    }
    override fun simulateUnreachable() = { google.failNext(503) }
}
