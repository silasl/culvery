package uk.co.siland.culvery.capability.reminders

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.access.test.TestAccess
import uk.co.siland.culvery.core.access.test.testAccess
import uk.co.siland.culvery.core.house.EventSeed
import uk.co.siland.culvery.core.house.EventTimes
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.repeat.RepeatEnd
import uk.co.siland.culvery.core.repeat.recurrenceRule
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemindersForEventsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: RemindersDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: RemindersStore
    private lateinit var household: HouseholdRepository

    private val london = ZoneId.of("Europe/London")

    /** Wednesday 18 November 2026, 09:00 in London. */
    private var nowMillis = at("2026-11-18T09:00:00")
    private val today = LocalDate.of(2026, 11, 18)
    private val friday = LocalDate.of(2026, 11, 20)
    private val overlay = TestOverlay()

    private val weekly = rule(RepeatChoice.Weekly)
    private val fortnightly = rule(RepeatChoice.Fortnightly)
    private val everyThreeMonths = "RRULE:FREQ=MONTHLY;INTERVAL=3"

    @Before
    fun setUp() = runTest {
        ShadowLog.clear()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Direct executors run every query on the test's thread, so a write has landed when the call that made it returns.
        db = Room.inMemoryDatabaseBuilder(context, RemindersDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        store = RemindersStore(db)
        household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
    }

    @After
    fun tearDown() {
        db.close()
        householdDb.close()
    }

    private fun at(text: String) = LocalDateTime.parse(text).atZone(london).toInstant().toEpochMilli()

    private fun rule(choice: RepeatChoice) = checkNotNull(recurrenceRule(choice, RepeatEnd.Never, LocalDate.of(2026, 11, 2)))

    private suspend fun TestScope.access(): TestAccess = testAccess(household, RemindersPermissionSource())

    private fun TestScope.actions(access: TestAccess) =
        RemindersActions(access.control, store, WallClock { nowMillis }, HouseholdZone(household), access.toasts, nightHours, backgroundScope)

    private val nightHours = object : uk.co.siland.culvery.core.plugin.NightHours {
        override fun isNight() = false

        override fun startsAt(): LocalTime? = LocalTime.of(22, 0)
    }

    private fun TestScope.forEvents(access: TestAccess) =
        RemindersForEvents(store, actions(access), household.members, WallClock { nowMillis }, HouseholdZone(household), backgroundScope)

    private fun seed(
        people: List<PersonId> = emptyList(),
        day: LocalDate = friday,
        time: LocalTime? = LocalTime.of(18, 30),
        rule: String? = null,
        now: EventTimes = EventTimes(day, time, rule),
    ) = EventSeed("event-1", "Swimming", people, day, time, rule, now)

    private fun form(seed: EventSeed, childOnly: PersonId? = null) = ReminderForm(null, today, LocalTime.of(9, 0), childOnly, seed)

    /** A reminder stored as remembering "event-1" as [link], made at [madeAt]. */
    private suspend fun linked(
        schedule: Schedule,
        time: LocalTime,
        link: EventLink,
        madeAt: Long = nowMillis,
    ): Long = checkNotNull(store.add(ReminderDraft("PE kit", listOf(PersonId.FAMILY), time, schedule, link), PersonId("sam"), madeAt))

    private fun link(day: LocalDate?, time: LocalTime?, rule: String? = null, follows: Boolean = false) =
        EventLink("event-1", day, time, rule, follows)

    private suspend fun stored() = store.reminders.first()

    private fun logs() = ShadowLog.getLogsForTag("Reminders").map { it.msg }

    @Test
    fun anEventWithNoReminderOpensTheFormFilledIn() {
        val alex = PersonId("alex")
        val seed = seed(people = listOf(alex), rule = everyThreeMonths)
        val opened = form(seed)
        assertThat(opened.words).isEqualTo("Swimming")
        assertThat(opened.who).containsExactly(alex)
        assertThat(opened.date).isEqualTo(friday)
        assertThat(opened.time).isEqualTo(LocalTime.of(18, 30))
        assertThat(opened.repeat.kept).isEqualTo(everyThreeMonths)
        assertThat(opened.edited).isFalse()
        assertThat(form(seed(people = emptyList())).who).containsExactly(PersonId.FAMILY)
        assertThat(form(seed().copy(title = "x".repeat(70))).words).hasLength(WORDS_MAX)

        runTest {
            forEvents(access()).open(overlay, seed)
        }
        compose.setContent { CulveryTheme(dark = false) { checkNotNull(overlay.content)() } }
        compose.onNodeWithTag("reminder_words").assertTextContains("Swimming")
        compose.onNodeWithTag("reminder_save").assertTextEquals("Add reminder")
        assertThat(compose.onAllNodesWithTag("repeat_kept").fetchSemanticsNodes()).hasSize(1)
        assertThat(compose.onAllNodesWithTag("reminder_delete").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun anAllDayEventOpensAtEight() {
        val opened = form(seed(time = null))
        assertThat(opened.time).isEqualTo(LocalTime.of(8, 0))
        assertThat(opened.toDraft().time).isEqualTo(LocalTime.of(8, 0))
    }

    @Test
    fun aChildsFormIsTheirOwn() {
        val mia = PersonId("mia")
        val opened = form(seed(people = listOf(PersonId("alex"), PersonId("sam"))), childOnly = mia)
        assertThat(opened.who).containsExactly(mia)
        opened.toggleWho(PersonId("alex"))
        assertThat(opened.toDraft().forPeople).containsExactly(mia)
    }

    @Test
    fun anEventWithAReminderOpensThatReminder() {
        runTest {
            val access = access()
            linked(Schedule.Once(friday), LocalTime.of(17, 0), link(friday, LocalTime.of(18, 30)))
            forEvents(access).open(overlay, seed())
            assertThat(access.requests).isEmpty()
        }
        compose.setContent { CulveryTheme(dark = false) { checkNotNull(overlay.content)() } }
        compose.onNodeWithTag("reminder_words").assertTextContains("PE kit")
        compose.onNodeWithTag("reminder_save").assertTextEquals("Save changes")
        assertThat(compose.onAllNodesWithTag("reminder_delete").fetchSemanticsNodes()).hasSize(1)
    }

    @Test
    fun aChildCannotOpenAnAdultsReminderFromItsEvent() = runTest {
        val access = access()
        linked(Schedule.Once(friday), LocalTime.of(17, 0), link(friday, LocalTime.of(18, 30)))
        access.answer(TestAccess.MIA)
        access.control.authorise(RemindersPermissions.ADD_SELF)
        forEvents(access).open(overlay, seed())
        assertThat(overlay.content).isNull()
        assertThat(access.toasts.messages).containsExactly("Mia can only change reminders they made.")
    }

    @Test
    fun savingRemembersTheEvent() = runTest {
        val access = access()
        val actions = actions(access)
        val now = EventTimes(LocalDate.of(2026, 11, 2), LocalTime.of(18, 30), weekly)
        val opened = form(seed(rule = weekly, now = now))
        access.answer(TestAccess.SAM)
        assertThat(actions.save(null, opened.toDraft())).isEqualTo(SaveResult.Saved)
        val saved = stored().single()
        assertThat(saved.event).isEqualTo(EventLink("event-1", now.day, now.time, weekly, followsRepeat = true))
        assertThat(saved.schedule).isEqualTo(Schedule.Repeating(friday, weekly))
        assertThat(store.hasEvent("event-1").first()).isTrue()
        assertThat(store.hasEvent("event-2").first()).isFalse()
    }

    @Test
    fun aSecondSaveForTheSameEventChangesTheFirst() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.SAM)
        assertThat(actions.save(null, form(seed()).toDraft())).isEqualTo(SaveResult.Saved)
        val second = form(seed()).apply { words = "Swimming bag" }
        access.control.lock()
        access.answer(TestAccess.SAM)
        assertThat(actions.save(null, second.toDraft())).isEqualTo(SaveResult.Saved)
        assertThat(stored().map { it.words }).containsExactly("Swimming bag")
        assertThat(access.requests.map { it.label }).containsExactly("Add a reminder", "Change this reminder").inOrder()
    }

    @Test
    fun aReminderOnTheEventsDayWithItsRepeatFollowsIt() {
        val opened = form(seed(rule = weekly))
        opened.time = LocalTime.of(17, 0)
        assertThat(opened.toDraft().event?.followsRepeat).isTrue()
    }

    @Test
    fun aReminderMovedToAnotherDayKeepsItsOwnRepeat() {
        val movedDay = form(seed(rule = weekly)).apply { date = friday.plusDays(1) }
        assertThat(movedDay.toDraft().event?.followsRepeat).isFalse()
        val ownRepeat = form(seed(rule = weekly)).apply { repeat.choose(RepeatChoice.Fortnightly) }
        assertThat(ownRepeat.toDraft().event?.followsRepeat).isFalse()
    }

    @Test
    fun goneDeletesWithNoToast() = runTest {
        val access = access()
        linked(Schedule.Once(friday), LocalTime.of(17, 0), link(friday, LocalTime.of(18, 30)))
        store.add(ReminderDraft("Bins", listOf(PersonId.FAMILY), LocalTime.of(19, 0), Schedule.Once(friday)), PersonId("sam"), nowMillis)
        assertThat(forEvents(access).gone(listOf("event-1", "event-2"))).isTrue()
        assertThat(stored().map { it.words }).containsExactly("Bins")
        assertThat(access.requests).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
        assertThat(logs()).containsExactly("event gone")
    }

    @Test
    fun goneForAnUnknownKeyIsFalse() = runTest {
        val access = access()
        linked(Schedule.Once(friday), LocalTime.of(17, 0), link(friday, LocalTime.of(18, 30)))
        assertThat(forEvents(access).gone(listOf("event-2"))).isFalse()
        assertThat(forEvents(access).gone(emptyList())).isFalse()
        assertThat(stored()).hasSize(1)
        assertThat(logs()).isEmpty()
    }

    @Test
    fun aMovedEventShiftsTheReminderByTheSameAmount() = runTest {
        val access = access()
        linked(Schedule.Once(friday), LocalTime.of(17, 30), link(friday, LocalTime.of(18, 30)))
        val now = EventTimes(friday.plusDays(1), LocalTime.of(19, 0), null)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-21T19:00:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.schedule).isEqualTo(Schedule.Once(friday.plusDays(1)))
        assertThat(moved.time).isEqualTo(LocalTime.of(18, 0))
        assertThat(moved.event).isEqualTo(link(now.day, now.time))
        assertThat(moved.answeredAtMillis).isEqualTo(nowMillis)
        assertThat(access.toasts.messages).isEmpty()
        assertThat(logs()).containsExactly("event moved")
    }

    @Test
    fun aOneOffShiftedAcrossMidnightChangesDay() = runTest {
        val access = access()
        val actions = actions(access)
        val opened = form(seed(time = LocalTime.of(23, 30))).apply { time = LocalTime.of(23, 45) }
        access.answer(TestAccess.SAM)
        actions.save(null, opened.toDraft())
        val now = EventTimes(friday.plusDays(1), LocalTime.of(0, 30), null)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-21T00:30:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.schedule).isEqualTo(Schedule.Once(friday.plusDays(1)))
        assertThat(moved.time).isEqualTo(LocalTime.of(0, 45))
    }

    @Test
    fun aOneOffEventsReminderMovesDayWithIt() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.SAM)
        actions.save(null, form(seed()).apply { time = LocalTime.of(17, 0) }.toDraft())
        assertThat(stored().single().event?.followsRepeat).isTrue()
        val now = EventTimes(friday.plusDays(3), LocalTime.of(18, 30), null)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-23T18:30:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.schedule).isEqualTo(Schedule.Once(friday.plusDays(3)))
        assertThat(moved.time).isEqualTo(LocalTime.of(17, 0))
    }

    @Test
    fun aSeriesMovedToAnotherWeekdayTakesAFollowerWithIt() = runTest {
        val access = access()
        linked(Schedule.Repeating(LocalDate.of(2026, 11, 23), weekly), LocalTime.of(17, 0), link(LocalDate.of(2026, 11, 2), LocalTime.of(18, 0), weekly, follows = true))
        val now = EventTimes(LocalDate.of(2026, 11, 3), LocalTime.of(19, 0), weekly)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-24T19:00:00"))).isTrue()
        val moved = stored().single()
        // Weekly from Tuesday 3 November: the 17th has passed, the 24th is next; the time moves by an hour, not a day and an hour.
        assertThat(moved.schedule).isEqualTo(Schedule.Repeating(LocalDate.of(2026, 11, 24), weekly))
        assertThat(moved.time).isEqualTo(LocalTime.of(18, 0))
    }

    @Test
    fun aReminderDeletedByAMoveLogsEventGoneOnly() = runTest {
        val access = access()
        linked(Schedule.Once(friday), LocalTime.of(17, 0), link(friday, LocalTime.of(18, 0)))
        val now = EventTimes(today.minusDays(1), LocalTime.of(18, 0), null)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-17T18:00:00"))).isFalse()
        assertThat(stored()).isEmpty()
        assertThat(logs()).containsExactly("event gone")
    }

    @Test
    fun aMonthlyRuleMovesToItsNewWeekday() = runTest {
        val access = access()
        val firstMonday = LocalDate.of(2026, 11, 2)
        val onMonday = checkNotNull(recurrenceRule(RepeatChoice.MonthlyOnWeekday, RepeatEnd.Never, firstMonday))
        linked(Schedule.Repeating(firstMonday, onMonday), LocalTime.of(17, 0), link(firstMonday, LocalTime.of(18, 0), onMonday))
        val firstTuesday = LocalDate.of(2026, 11, 3)
        val now = EventTimes(firstTuesday, LocalTime.of(18, 0), onMonday)
        assertThat(forEvents(access).changed("event-1", now, at("2026-12-01T18:00:00"))).isTrue()
        assertThat(stored().single().schedule)
            .isEqualTo(Schedule.Repeating(firstTuesday, checkNotNull(recurrenceRule(RepeatChoice.MonthlyOnWeekday, RepeatEnd.Never, firstTuesday))))
    }

    @Test
    fun aMonthlyByDateRuleTheMoveCantShowIsLeftAlone() = runTest {
        val access = access()
        val byDate = "RRULE:FREQ=MONTHLY;BYMONTHDAY=2"
        linked(Schedule.Repeating(LocalDate.of(2026, 11, 2), byDate), LocalTime.of(17, 0), link(LocalDate.of(2026, 11, 2), LocalTime.of(18, 0), byDate))
        val now = EventTimes(LocalDate.of(2026, 11, 9), LocalTime.of(18, 0), byDate)
        assertThat(forEvents(access).changed("event-1", now, at("2026-12-09T18:00:00"))).isTrue()
        assertThat(stored().single().schedule).isEqualTo(Schedule.Repeating(LocalDate.of(2026, 11, 9), byDate))
    }

    @Test
    fun theSameTimesChangeNothing() = runTest {
        val access = access()
        linked(Schedule.Repeating(friday, weekly), LocalTime.of(17, 0), link(LocalDate.of(2026, 11, 6), LocalTime.of(18, 30), weekly, follows = true), madeAt = at("2026-11-18T08:00:00"))
        val before = stored().single()
        val same = EventTimes(LocalDate.of(2026, 11, 6), LocalTime.of(18, 30), weekly)
        assertThat(forEvents(access).changed("event-1", same, at("2026-11-20T18:30:00"))).isFalse()
        assertThat(forEvents(access).changed("event-2", same, at("2026-11-20T18:30:00"))).isFalse()
        assertThat(stored().single()).isEqualTo(before)
        assertThat(logs()).isEmpty()
    }

    @Test
    fun aNewRuleReplacesAFollowingRemindersRule() = runTest {
        val access = access()
        val seriesStart = LocalDate.of(2026, 11, 2)
        linked(Schedule.Repeating(LocalDate.of(2026, 11, 23), weekly), LocalTime.of(17, 0), link(seriesStart, LocalTime.of(18, 0), weekly, follows = true))
        val now = EventTimes(seriesStart, LocalTime.of(18, 0), fortnightly)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-30T18:00:00"))).isTrue()
        val moved = stored().single()
        // Every two weeks from Monday 2 November: the 16th has passed, the 30th is next.
        assertThat(moved.schedule).isEqualTo(Schedule.Repeating(LocalDate.of(2026, 11, 30), fortnightly))
        assertThat(moved.time).isEqualTo(LocalTime.of(17, 0))
        assertThat(moved.event).isEqualTo(link(seriesStart, LocalTime.of(18, 0), fortnightly, follows = true))
    }

    @Test
    fun aNewRuleLeavesAnOwnRepeatAlone() = runTest {
        val access = access()
        val seriesStart = LocalDate.of(2026, 11, 2)
        val own = weeklyOn(DayOfWeek.TUESDAY)
        linked(own, LocalTime.of(17, 0), link(seriesStart, LocalTime.of(18, 0), weekly))
        val now = EventTimes(seriesStart, LocalTime.of(18, 0), fortnightly)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-30T18:00:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.schedule).isEqualTo(own)
        assertThat(moved.event?.rule).isEqualTo(fortnightly)
    }

    @Test
    fun aMovedOwnRepeatTakesItsOtherDaysAlong() = runTest {
        val access = access()
        linked(weeklyOn(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(17, 0), link(LocalDate.of(2026, 11, 2), LocalTime.of(18, 0), weekly))
        val now = EventTimes(LocalDate.of(2026, 11, 3), LocalTime.of(18, 0), weekly)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-24T18:00:00"))).isTrue()
        assertThat(stored().single().schedule).isEqualTo(weeklyOn(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
    }

    @Test
    fun aKeptOwnRepeatIsLeftAsItWas() = runTest {
        val access = access()
        val kept = "RRULE:FREQ=WEEKLY;INTERVAL=3;BYDAY=MO,WE"
        linked(Schedule.Repeating(LocalDate.of(2026, 11, 2), kept), LocalTime.of(17, 0), link(LocalDate.of(2026, 11, 2), LocalTime.of(18, 0), kept))
        val now = EventTimes(LocalDate.of(2026, 11, 3), LocalTime.of(18, 0), kept)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-24T18:00:00"))).isTrue()
        assertThat(stored().single().schedule).isEqualTo(Schedule.Repeating(LocalDate.of(2026, 11, 3), kept))
    }

    @Test
    fun aFollowingReminderStopsAtTheEndOfItsDay() = runTest {
        val access = access()
        val seriesStart = LocalDate.of(2026, 11, 2)
        linked(Schedule.Repeating(LocalDate.of(2026, 11, 23), weekly), LocalTime.of(23, 30), link(seriesStart, LocalTime.of(18, 0), weekly, follows = true))
        val now = EventTimes(seriesStart, LocalTime.of(19, 0), weekly)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-23T19:00:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.schedule).isEqualTo(Schedule.Repeating(LocalDate.of(2026, 11, 23), weekly))
        assertThat(moved.time).isEqualTo(LocalTime.of(23, 55))
    }

    @Test
    fun aSeriesThatHasEndedDeletesAFollowingReminder() = runTest {
        val access = access()
        val seriesStart = LocalDate.of(2026, 11, 2)
        linked(Schedule.Repeating(LocalDate.of(2026, 11, 23), weekly), LocalTime.of(17, 0), link(seriesStart, LocalTime.of(18, 0), weekly, follows = true))
        val ended = "$weekly;UNTIL=20261110"
        // A start given anyway, so it is the ended rule that deletes it.
        assertThat(forEvents(access).changed("event-1", EventTimes(seriesStart, LocalTime.of(18, 0), ended), at("2026-11-09T18:00:00"))).isFalse()
        assertThat(stored()).isEmpty()
        assertThat(logs()).containsExactly("event gone")
    }

    @Test
    fun aSeriesWithNoDateLeftDeletesItsReminder() = runTest {
        val access = access()
        linked(weeklyOn(DayOfWeek.TUESDAY), LocalTime.of(17, 0), link(LocalDate.of(2026, 11, 2), LocalTime.of(18, 0), weekly))
        assertThat(forEvents(access).changed("event-1", EventTimes(LocalDate.of(2026, 11, 2), LocalTime.of(19, 0), weekly), null)).isFalse()
        assertThat(stored()).isEmpty()
    }

    @Test
    fun anEventMovedIntoThePastDeletesAOneOff() = runTest {
        val access = access()
        linked(Schedule.Once(friday), LocalTime.of(17, 0), link(friday, LocalTime.of(18, 0)))
        val now = EventTimes(today.minusDays(1), LocalTime.of(18, 0), null)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-17T18:00:00"))).isFalse()
        assertThat(stored()).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun anAllDayEventMovedToTodayKeepsItsReminder() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.SAM)
        actions.save(null, form(seed(time = null)).toDraft())
        val now = EventTimes(today, null, null)
        // An all-day event starts, for this rule, at the end of its day.
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-19T00:00:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.schedule).isEqualTo(Schedule.Once(today))
        assertThat(moved.time).isEqualTo(LocalTime.of(8, 0))
        assertThat(moved.due(nowMillis, london)?.dueAtMillis).isEqualTo(at("2026-11-18T08:00:00"))
    }

    @Test
    fun aReminderWhoseOwnTimeHasPassedShowsNow() = runTest {
        val access = access()
        linked(Schedule.Once(today), LocalTime.of(10, 15), link(today, LocalTime.of(18, 0)))
        nowMillis = at("2026-11-18T10:00:00")
        val now = EventTimes(today, LocalTime.of(17, 0), null)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-18T17:00:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.time).isEqualTo(LocalTime.of(9, 15))
        assertThat(moved.due(nowMillis, london)?.dueAtMillis).isEqualTo(at("2026-11-18T09:15:00"))
    }

    @Test
    fun aReminderMovedToBeforeItWasMadeStillShows() = runTest {
        val access = access()
        linked(Schedule.Once(today), LocalTime.of(13, 0), link(today, LocalTime.of(17, 0)))
        nowMillis = at("2026-11-18T10:00:00")
        val now = EventTimes(today, LocalTime.of(11, 0), null)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-18T11:00:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.time).isEqualTo(LocalTime.of(7, 0))
        assertThat(moved.due(nowMillis, london)?.dueAtMillis).isEqualTo(at("2026-11-18T07:00:00"))
    }

    @Test
    fun aReminderMovedLaterWaitsAgain() = runTest {
        val access = access()
        linked(Schedule.Once(today), LocalTime.of(8, 30), link(today, LocalTime.of(18, 0)), madeAt = at("2026-11-18T07:00:00"))
        store.later(stored().single().id, nowMillis)
        val now = EventTimes(today, LocalTime.of(20, 0), null)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-18T20:00:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.time).isEqualTo(LocalTime.of(10, 30))
        assertThat(moved.answeredAtMillis).isEqualTo(nowMillis)
        assertThat(moved.snoozedUntilMillis).isNull()
        assertThat(moved.due(nowMillis, london)).isNull()
    }

    @Test
    fun aSeriesWithNoKnownFirstDayOnlyShiftsTheTime() = runTest {
        val access = access()
        val own = Schedule.Repeating(LocalDate.of(2026, 11, 25), weekly)
        linked(own, LocalTime.of(17, 30), link(null, LocalTime.of(18, 0), weekly))
        val now = EventTimes(null, LocalTime.of(19, 0), weekly)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-25T19:00:00"))).isTrue()
        val moved = stored().single()
        assertThat(moved.schedule).isEqualTo(own)
        assertThat(moved.time).isEqualTo(LocalTime.of(18, 30))
    }

    @Test
    fun changingTheDayOnTheTabStopsFollowingTheRepeat() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.SAM)
        actions.save(null, form(seed(rule = weekly, now = EventTimes(LocalDate.of(2026, 11, 6), LocalTime.of(18, 30), weekly))).toDraft())
        val saved = stored().single()
        assertThat(saved.event?.followsRepeat).isTrue()
        val onTheTab = ReminderForm(saved, today, LocalTime.of(9, 0), null).apply { date = friday.plusDays(1) }
        assertThat(actions.save(saved.id, onTheTab.toDraft())).isEqualTo(SaveResult.Saved)
        assertThat(stored().single().event?.followsRepeat).isFalse()

        val now = EventTimes(LocalDate.of(2026, 11, 6), LocalTime.of(18, 30), fortnightly)
        assertThat(forEvents(access).changed("event-1", now, at("2026-11-20T18:30:00"))).isTrue()
        assertThat(stored().single().schedule).isEqualTo(Schedule.Repeating(friday.plusDays(1), weekly))
    }

    @Test
    fun theLogLinesCarryNothingElse() = runTest {
        val access = access()
        linked(Schedule.Once(friday), LocalTime.of(17, 0), link(friday, LocalTime.of(18, 0)))
        forEvents(access).changed("event-1", EventTimes(friday, LocalTime.of(19, 0), null), at("2026-11-20T19:00:00"))
        forEvents(access).gone(listOf("event-1"))
        assertThat(logs()).containsExactly("event moved", "event gone").inOrder()
        assertThat(seed().toString()).isEqualTo("EventSeed")
        assertThat(EventTimes(friday, LocalTime.of(18, 0), weekly).toString()).isEqualTo("EventTimes")
    }
}
