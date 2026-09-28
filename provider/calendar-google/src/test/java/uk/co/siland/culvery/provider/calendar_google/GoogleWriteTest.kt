package uk.co.siland.culvery.provider.calendar_google

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.EVENT_GONE
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.core.plugin.Connection

@RunWith(AndroidJUnit4::class)
class GoogleWriteTest {
    private val london = ZoneId.of("Europe/London")
    private val google = FakeGoogleServer(london)
    private lateinit var api: GoogleApi
    private lateinit var provider: GoogleCalendarProvider
    private val conn = Connection("g1", GOOGLE_PROVIDER_ID, "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val family = CalendarSource("family@example.com", "Family", writable = true, primary = true)
    private val holidays = CalendarSource("holidays@group", "UK holidays", writable = false)
    private val key = "0123456789abcdef0123456789abcdef"

    @Before
    fun setUp() {
        api = GoogleApi(google.start(), FakeTokenSource(), OkHttpClient())
        provider = GoogleCalendarProvider(api, FakeAuthorizer(), NoToasts)
        google.addCalendar(family.id, "Family", primary = true)
        google.addCalendar(holidays.id, "UK holidays", accessRole = "reader")
    }

    @After
    fun tearDown() {
        google.shutdown()
        assertLogsHoldNoPersonalData("Swim", "UK holidays", "mia-id", "sam-id", key)
    }

    private fun at(day: Int, hour: Int): Instant = LocalDate.of(2026, 9, day).atTime(hour, 0).atZone(london).toInstant()

    private fun draft(title: String = "Swim", forPerson: String? = "mia-id", color: Long? = 0xFFE07BA8) =
        EventDraft(title, EventTime.Timed(at(23, 16)), EventTime.Timed(at(23, 17)), forPerson, "sam-id", color)

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    @Test
    fun anInsertUsesTheKeyAsItsIdWithTheTagsColourAndUtcTimes() = runTest {
        val made = provider.create(conn, family, draft(), key)
        assertThat(made.remoteId).isEqualTo(key)
        val sent = google.bodies.single()
        assertThat(sent.text("id")).isEqualTo(key)
        assertThat(sent.text("summary")).isEqualTo("Swim")
        assertThat(sent.obj("start").text("dateTime")).isEqualTo("2026-09-23T15:00:00Z")
        assertThat(sent.obj("start")["timeZone"]).isNull()
        assertThat(sent.obj("extendedProperties").obj("private")).isEqualTo(
            JsonObject(mapOf(PERSON_KEY to JsonPrimitive("mia-id"), CREATED_BY_KEY to JsonPrimitive("sam-id"))),
        )
        assertThat(sent.text("colorId")).isEqualTo("4")
    }

    @Test
    fun anAllDayInsertSendsDatesAndFamilySendsNoColour() = runTest {
        val bins = EventDraft("Bin day", EventTime.AllDay(LocalDate.of(2026, 9, 24)), EventTime.AllDay(LocalDate.of(2026, 9, 25)), "family", "sam-id")
        provider.create(conn, family, bins, key)
        val sent = google.bodies.single()
        assertThat(sent.obj("start").text("date")).isEqualTo("2026-09-24")
        assertThat(sent.obj("end").text("date")).isEqualTo("2026-09-25")
        assertThat(sent["colorId"]).isNull()
    }

    @Test
    fun aRepeatedInsertReturnsTheEventItsKeyMade() = runTest {
        provider.create(conn, family, draft(), key)
        // The reply was lost; the queued create is sent again with the same key.
        val again = provider.create(conn, family, draft(), key)
        assertThat(again.remoteId).isEqualTo(key)
        assertThat(again.title).isEqualTo("Swim")
        assertThat(google.requests.map { it.method }).containsExactly("POST", "POST", "GET").inOrder()
    }

    @Test
    fun anInsertWhoseEventWasDeletedOnAPhoneIsRefused() = runTest {
        provider.create(conn, family, draft(), key)
        google.cancel(family.id, key)
        val error = failureOf { provider.create(conn, family, draft(), key) }
        assertThat(error).isInstanceOf(WriteRejectedException::class.java)
        assertThat(error?.message).isEqualTo(EVENT_GONE)
        assertThat(google.event(family.id, key)!!.text("status")).isEqualTo("cancelled")
    }

    @Test
    fun aPatchSendsOnlyItsFields() = runTest {
        provider.create(conn, family, draft(), key)
        provider.update(conn, family, key, draft(title = "Swim club"), setOf(EventField.TITLE))
        assertThat(google.bodies.last().keys).containsExactly("summary")
        provider.update(conn, family, key, draft(), setOf(EventField.TIMES))
        assertThat(google.bodies.last().keys).containsExactly("start", "end")
        assertThat(google.bodies.last().obj("start")["date"]).isEqualTo(JsonNull)
        provider.update(conn, family, key, draft(forPerson = "sam-id", color = 0xFF5B9BE0), setOf(EventField.FOR_PERSON))
        assertThat(google.bodies.last().keys).containsExactly("extendedProperties", "colorId")
        assertThat(google.bodies.last().text("colorId")).isEqualTo("9")
    }

    @Test
    fun aTitlePatchKeepsAPhoneChangeToTheTime() = runTest {
        provider.create(conn, family, draft(), key)
        // A phone moves it to 18:00 while the tablet's title edit waits.
        google.putEvent(family.id, google.timed(key, "Swim", at(23, 18), at(23, 19)))
        val updated = provider.update(conn, family, key, draft(title = "Swim club"), setOf(EventField.TITLE))
        assertThat(updated.title).isEqualTo("Swim club")
        assertThat(updated.start).isEqualTo(EventTime.Timed(at(23, 18)))
    }

    @Test
    fun aPersonPatchKeepsCreatedBy() = runTest {
        provider.create(conn, family, draft(), key)
        // The draft names someone else as its creator: a Who change must never send it.
        val retag = draft(forPerson = "alex-id").copy(createdBy = "someone-else")
        val updated = provider.update(conn, family, key, retag, setOf(EventField.FOR_PERSON))
        assertThat(google.bodies.last().obj("extendedProperties").obj("private").keys).containsExactly(PERSON_KEY)
        assertThat(updated.forPerson to updated.createdBy).isEqualTo("alex-id" to "sam-id")
        val stored = google.event(family.id, key)!!.obj("extendedProperties").obj("private")
        assertThat(stored.keys).containsExactly(PERSON_KEY, CREATED_BY_KEY)
        assertThat(stored.text(CREATED_BY_KEY)).isEqualTo("sam-id")
    }

    @Test
    fun whoChangedToFamilyClearsTheColour() = runTest {
        provider.create(conn, family, draft(), key)
        provider.update(conn, family, key, draft(forPerson = "family", color = null), setOf(EventField.FOR_PERSON))
        assertThat(google.bodies.last()["colorId"]).isEqualTo(JsonNull)
        assertThat(google.event(family.id, key)!!["colorId"]).isNull()
    }

    @Test
    fun aTimedEventMadeAllDayLosesItsTime() = runTest {
        provider.create(conn, family, draft(), key)
        val allDay = EventDraft("Swim", EventTime.AllDay(LocalDate.of(2026, 9, 23)), EventTime.AllDay(LocalDate.of(2026, 9, 24)), null, null)
        val updated = provider.update(conn, family, key, allDay, setOf(EventField.TIMES))
        assertThat(updated.start).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 9, 23)))
        assertThat(google.event(family.id, key)!!.obj("start").keys).containsExactly("date")
    }

    @Test
    fun updatingAnEventThatIsGoneIsRefusedAsGone() = runTest {
        assertThat(failureOf { provider.update(conn, family, "nope0", draft(), setOf(EventField.TITLE)) }?.message).isEqualTo(EVENT_GONE)
    }

    @Test
    fun anEditOfAnEventCancelledOnThePhoneIsGoneAndNeverBringsItBack() = runTest {
        provider.create(conn, family, draft(), key)
        google.cancel(family.id, key)
        // Google would answer a PATCH here with 200, so the edit must look first and send nothing.
        assertThat(failureOf { provider.update(conn, family, key, draft(title = "Swim club"), setOf(EventField.TITLE)) }?.message)
            .isEqualTo(EVENT_GONE)
        assertThat(google.requests.map { it.method }).doesNotContain("PATCH")
        assertThat(google.event(family.id, key)!!.text("status")).isEqualTo("cancelled")
        // Cancelled between the look and the PATCH: the answer says so, and it is still gone.
        provider.create(conn, family, draft(), "abcdefabcdef")
        val cancelledMeanwhile = JsonObject(google.event(family.id, "abcdefabcdef")!! + ("status" to JsonPrimitive("cancelled")))
        google.failNextWith(MockResponse().setBody(cancelledMeanwhile.toString())) { it.method == "PATCH" }
        assertThat(failureOf { provider.update(conn, family, "abcdefabcdef", draft(), setOf(EventField.TITLE)) }?.message)
            .isEqualTo(EVENT_GONE)
    }

    @Test
    fun theFakeAsGoogleAnswersAPatchOnACancelledEventWith200AndLeavesItCancelled() = runTest {
        provider.create(conn, family, draft(), key)
        google.cancel(family.id, key)
        val url = api.url("calendars", family.id, "events", key)
        val answer = api.send("family@example.com", "PATCH", url, patchBody(draft(title = "Swim club"), setOf(EventField.TITLE)))
        assertThat(answer.code).isEqualTo(200)
        assertThat(google.event(family.id, key)!!.text("status")).isEqualTo("cancelled")
    }

    @Test
    fun anEditOfAnEventMadeASeriesOnThePhoneIsRefused() = runTest {
        provider.create(conn, family, draft(), key)
        google.putEvent(family.id, google.timed(key, "Swim", at(23, 16), at(23, 17)) { putJsonArray("recurrence") { add("RRULE:FREQ=WEEKLY") } })
        // A PATCH would change every instance of the series.
        assertThat(failureOf { provider.update(conn, family, key, draft(title = "Swim club"), setOf(EventField.TITLE)) }?.message)
            .isEqualTo("the change was refused")
        assertThat(google.requests.map { it.method }).doesNotContain("PATCH")
        assertLogsHoldNoPersonalData(minLines = 1)
    }

    @Test
    fun aDeleteOfAnEventCancelledOnThePhoneSucceedsAndSendsNoDelete() = runTest {
        provider.create(conn, family, draft(), key)
        google.cancel(family.id, key)
        provider.delete(conn, family, key)
        assertThat(google.requests.map { it.method }).doesNotContain("DELETE")
    }

    @Test
    fun aDeleteOfAnEventMadeASeriesOnThePhoneIsRefused() = runTest {
        provider.create(conn, family, draft(), key)
        google.putEvent(family.id, google.timed(key, "Swim", at(23, 16), at(23, 17)) { putJsonArray("recurrence") { add("RRULE:FREQ=WEEKLY") } })
        // A DELETE would remove the whole series.
        assertThat(failureOf { provider.delete(conn, family, key) }?.message).isEqualTo("the change was refused")
        assertThat(google.requests.map { it.method }).doesNotContain("DELETE")
        assertThat(google.event(family.id, key)!!.text("status")).isEqualTo("confirmed")
        assertLogsHoldNoPersonalData(minLines = 1)
    }

    @Test
    fun aLookBeforeAWriteThatGoogleRefusesIsARefusalNotTryLater() = runTest {
        provider.create(conn, family, draft(), key)
        google.failNext(400, "invalid") { it.method == "GET" }
        val refused = failureOf { provider.update(conn, family, key, draft(title = "Swim club"), setOf(EventField.TITLE)) }
        assertThat(refused).isInstanceOf(WriteRejectedException::class.java)
        assertThat(refused?.message).isEqualTo(REFUSED)
        google.failNext(403, "forbidden") { it.method == "GET" }
        assertThat(failureOf { provider.delete(conn, family, key) }?.message).isEqualTo("this calendar can't be changed from the tablet")
        assertThat(google.requests.map { it.method }).containsNoneOf("PATCH", "DELETE")
        assertLogsHoldNoPersonalData(minLines = 2)
    }

    @Test
    fun deletingSucceedsWhateverIsLeft() = runTest {
        provider.create(conn, family, draft(), key)
        provider.delete(conn, family, key)
        // Looked up as cancelled: already deleted; 404: never there.
        provider.delete(conn, family, key)
        provider.delete(conn, family, "nope0")
        assertThat(google.event(family.id, key)!!.text("status")).isEqualTo("cancelled")
    }

    @Test
    fun aDeleteAnswered410Or404AfterTheLookSucceeds() = runTest {
        provider.create(conn, family, draft(), key)
        google.failNext(410, "deleted") { it.method == "DELETE" }
        provider.delete(conn, family, key)
        google.failNext(404, "notFound") { it.method == "DELETE" }
        provider.delete(conn, family, key)
        assertThat(google.requests.count { it.method == "DELETE" }).isEqualTo(2)
    }

    @Test
    fun findReturnsTheEventOrNull() = runTest {
        provider.create(conn, family, draft(), key)
        assertThat(provider.find(conn, family, key)?.title).isEqualTo("Swim")
        assertThat(provider.find(conn, family, "nope0")).isNull()
        google.cancel(family.id, key)
        assertThat(provider.find(conn, family, key)).isNull()
    }

    @Test
    fun refusalsUseTheTabletsWordsNotGooglesAndTheOthersAreRetried() = runTest {
        assertThat(failureOf { provider.create(conn, holidays, draft(), key) }?.message).isEqualTo("this calendar can't be changed from the tablet")
        google.failNext(400, "invalid")
        assertThat(failureOf { provider.create(conn, family, draft(), key) }?.message).isEqualTo("the change was refused")
        google.failNext(401, "authError")
        google.failNext(401, "authError")
        assertThat(failureOf { provider.create(conn, family, draft(), key) }).isInstanceOf(NeedsSignInException::class.java)
        google.failNext(503)
        assertThat(failureOf { provider.create(conn, family, draft(), key) }).isInstanceOf(UnreachableException::class.java)
        // The two refusals and the 503.
        assertLogsHoldNoPersonalData(minLines = 3)
    }
}
