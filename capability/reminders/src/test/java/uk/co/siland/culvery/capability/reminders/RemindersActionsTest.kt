package uk.co.siland.culvery.capability.reminders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.access.test.TestAccess
import uk.co.siland.culvery.core.access.test.testAccess
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.test.awaiting

@RunWith(AndroidJUnit4::class)
class RemindersActionsTest {
    private lateinit var db: RemindersDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: RemindersStore
    private lateinit var household: HouseholdRepository

    private val london = ZoneId.of("Europe/London")

    /** Wednesday 18 November 2026, 09:00 in London. */
    private var nowMillis = at("2026-11-18T09:00:00")
    private val today = LocalDate.of(2026, 11, 18)

    @Before
    fun setUp() = runTest {
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

    private suspend fun TestScope.access(): TestAccess = testAccess(household, RemindersPermissionSource())

    private fun TestScope.actions(access: TestAccess, clock: WallClock = WallClock { nowMillis }) =
        RemindersActions(access.control, store, clock, HouseholdZone(household), access.toasts, backgroundScope)

    private fun once(who: List<PersonId>, words: String = "PE kit", time: LocalTime = LocalTime.of(16, 30), on: LocalDate = today) =
        ReminderDraft(words, who, time, Schedule.Once(on))

    private fun weekly(who: List<PersonId>, vararg days: DayOfWeek) =
        ReminderDraft("Swimming bag", who, LocalTime.of(8, 0), Schedule.Weekly(days.toSet()))

    private suspend fun stored() = store.reminders.first()

    private suspend fun seed(draft: ReminderDraft, madeBy: PersonId, madeAt: Long = nowMillis) = store.add(draft, madeBy, madeAt)!!

    @Test
    fun anAdultAddsForAnyone() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.SAM)
        assertThat(actions.save(null, once(listOf(access.mia.id), words = "  PE \n kit "))).isEqualTo(SaveResult.Saved)
        assertThat(actions.save(null, once(listOf(PersonId.FAMILY), words = "Bins"))).isEqualTo(SaveResult.Saved)
        assertThat(stored().map { Triple(it.words, it.forPeople, it.madeBy) }).containsExactly(
            Triple("PE kit", listOf(access.mia.id), access.sam.id),
            Triple("Bins", listOf(PersonId.FAMILY), access.sam.id),
        ).inOrder()
        // The second save rode the session the first PIN started.
        assertThat(access.requests.map { it.label }).containsExactly("Add a reminder")
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun aChildAddsForThemselvesAndMayRepeat() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.MIA)
        assertThat(actions.save(null, weekly(listOf(access.mia.id), DayOfWeek.MONDAY, DayOfWeek.FRIDAY))).isEqualTo(SaveResult.Saved)
        val saved = stored().single()
        assertThat(saved.madeBy).isEqualTo(access.mia.id)
        assertThat(saved.schedule).isEqualTo(Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
    }

    @Test
    fun aChildsSaveForSomeoneElseIsRefusedByName() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.MIA)
        assertThat(actions.save(null, once(listOf(access.alex.id)))).isEqualTo(SaveResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can add reminders for Mia only.")
        assertThat(stored()).isEmpty()
        assertThat(access.control.session.value).isNull()

        access.answer(TestAccess.MIA)
        assertThat(actions.save(null, once(listOf(access.mia.id, access.alex.id)))).isEqualTo(SaveResult.Cancelled)
        access.answer(TestAccess.MIA)
        assertThat(actions.save(null, once(listOf(PersonId.FAMILY)))).isEqualTo(SaveResult.Cancelled)
        assertThat(stored()).isEmpty()
    }

    @Test
    fun aCancelledPinPadSavesNothing() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(null)
        assertThat(actions.save(null, once(listOf(access.mia.id)))).isEqualTo(SaveResult.Cancelled)
        assertThat(stored()).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun openingNeedsNoPin() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.alex.id)), access.alex.id)
        val opened = actions.openExisting(id)
        assertThat(opened?.id).isEqualTo(id)
        assertThat(opened?.words).isEqualTo("PE kit")
        assertThat(access.requests).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
        assertThat(actions.openExisting(id + 1)).isNull()
        assertThat(access.requests).isEmpty()
    }

    private suspend fun signInMia(access: TestAccess) {
        access.answer(TestAccess.MIA)
        access.control.authorise(RemindersPermissions.ADD_SELF)
    }

    @Test
    fun aChildHereCannotOpenAnAdults() = runTest {
        val access = access()
        val actions = actions(access)
        val adults = seed(once(listOf(access.alex.id)), access.alex.id)
        val hers = seed(once(listOf(access.mia.id)), access.mia.id)
        signInMia(access)
        assertThat(actions.openExisting(adults)).isNull()
        assertThat(access.toasts.messages).containsExactly("Mia can only change reminders they made.")
        assertThat(access.toasts.urgent).containsExactly("Mia can only change reminders they made.")
        assertThat(actions.openExisting(hers)?.id).isEqualTo(hers)
        assertThat(access.requests).hasSize(1)
    }

    @Test
    fun aChildsSaveOfAnAdultsIsRefusedByName() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.alex.id)), access.alex.id)
        access.answer(TestAccess.MIA)
        assertThat(actions.save(id, once(listOf(access.alex.id), words = "Changed"))).isEqualTo(SaveResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change reminders they made.")
        assertThat(stored().single().words).isEqualTo("PE kit")
    }

    @Test
    fun aChildCannotPassTheirReminderToSomeoneElse() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.mia.id)), access.mia.id)
        access.answer(TestAccess.MIA)
        assertThat(actions.save(id, once(listOf(access.alex.id)))).isEqualTo(SaveResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can add reminders for Mia only.")
        assertThat(stored().single().forPeople).containsExactly(access.mia.id)

        access.answer(TestAccess.MIA)
        assertThat(actions.save(id, once(listOf(access.mia.id), words = "Own words"))).isEqualTo(SaveResult.Saved)
        assertThat(stored().single().words).isEqualTo("Own words")
        assertThat(access.requests.map { it.label }).containsExactly("Change this reminder", "Change this reminder")
    }

    @Test
    fun anAdultChangesAnyonesAndWhoItIsFor() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.mia.id)), access.mia.id)
        access.answer(TestAccess.SAM)
        assertThat(actions.save(id, once(listOf(access.alex.id, access.mia.id), words = "Both"))).isEqualTo(SaveResult.Saved)
        val changed = stored().single()
        assertThat(changed.forPeople).containsExactly(access.alex.id, access.mia.id).inOrder()
        assertThat(changed.madeBy).isEqualTo(access.mia.id)
    }

    @Test
    fun aChildCannotDeleteAnAdults() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.alex.id)), access.alex.id)
        access.answer(TestAccess.MIA)
        assertThat(actions.delete(id)).isFalse()
        assertThat(access.toasts.messages).containsExactly("Mia can only change reminders they made.")
        assertThat(stored()).hasSize(1)
        assertThat(access.requests.map { it.label }).containsExactly("Change this reminder")
    }

    @Test
    fun madeByIsWhoeverAuthorisedTheSave() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.SAM)
        assertThat(actions.save(null, once(listOf(access.alex.id)))).isEqualTo(SaveResult.Saved)
        assertThat(stored().single().madeBy).isEqualTo(access.sam.id)
    }

    @Test
    fun childHereIsOnlyAChildsLiveSession() = runTest {
        val access = access()
        val actions = actions(access)
        assertThat(actions.childHere()).isNull()
        access.answer(TestAccess.SAM)
        access.control.authorise(RemindersPermissions.ADD_SELF)
        assertThat(actions.childHere()).isNull()
        access.control.lock()
        assertThat(actions.childHere()).isNull()
        signInMia(access)
        assertThat(actions.childHere()).isEqualTo(access.mia.id)
        access.control.lock()
        assertThat(actions.childHere()).isNull()
        assertThat(access.requests).hasSize(2)
    }

    @Test
    fun aTimeThatPassedWhileThePinPadWasUpIsRefused() = runTest {
        val access = access()
        // The pad opens at 09:00 and is answered at 16:31.
        val actions = actions(access, WallClock { if (access.requests.isEmpty()) nowMillis else at("2026-11-18T16:31:00") })
        access.answer(TestAccess.ALEX)
        assertThat(actions.save(null, once(listOf(access.alex.id), time = LocalTime.of(16, 30)))).isEqualTo(SaveResult.Past)
        assertThat(stored()).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun aTimeLaterTodayIsFineAndOneEarlierTodayIsNot() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.ALEX)
        assertThat(actions.save(null, once(listOf(access.alex.id), time = LocalTime.of(9, 0)))).isEqualTo(SaveResult.Past)
        assertThat(actions.save(null, once(listOf(access.alex.id), time = LocalTime.of(9, 1)))).isEqualTo(SaveResult.Saved)
        assertThat(actions.save(null, once(listOf(access.alex.id), time = LocalTime.of(8, 0), on = today.plusDays(1)))).isEqualTo(SaveResult.Saved)
        assertThat(actions.save(null, weekly(listOf(access.alex.id), DayOfWeek.WEDNESDAY))).isEqualTo(SaveResult.Saved)
        assertThat(stored()).hasSize(3)
    }

    private suspend fun waitingOneOff(access: TestAccess, on: LocalDate = today, at: LocalTime = LocalTime.of(8, 0)): Long =
        seed(once(listOf(access.alex.id), time = at, on = on), access.alex.id, madeAt = at("2026-11-17T20:00:00"))

    @Test
    fun aWaitingOneOffsWordsCanChange() = runTest {
        val access = access()
        val actions = actions(access)
        val id = waitingOneOff(access)
        assertThat(stored().single().due(nowMillis, london)).isNotNull()
        access.answer(TestAccess.ALEX)
        assertThat(actions.save(id, once(listOf(access.alex.id), words = "New words", time = LocalTime.of(8, 0)))).isEqualTo(SaveResult.Saved)
        val changed = stored().single()
        assertThat(changed.words).isEqualTo("New words")
        assertThat(changed.due(nowMillis, london)).isNotNull()
    }

    @Test
    fun aOneOffFromYesterdayStillWaitingKeepsItsDayWhenItsWordsChange() = runTest {
        val access = access()
        val actions = actions(access)
        val id = waitingOneOff(access, on = today.minusDays(1))
        access.answer(TestAccess.ALEX)
        val draft = once(listOf(access.alex.id), words = "New words", time = LocalTime.of(8, 0), on = today.minusDays(1))
        assertThat(actions.save(id, draft)).isEqualTo(SaveResult.Saved)
        assertThat(stored().single().schedule).isEqualTo(Schedule.Once(today.minusDays(1)))
        assertThat(actions.save(id, draft.copy(schedule = Schedule.Once(today.minusDays(2))))).isEqualTo(SaveResult.Past)
    }

    @Test
    fun aWaitingOneOffMovedToAnotherPastTimeIsRefused() = runTest {
        val access = access()
        val actions = actions(access)
        val id = waitingOneOff(access)
        access.answer(TestAccess.ALEX)
        assertThat(actions.save(id, once(listOf(access.alex.id), time = LocalTime.of(8, 30)))).isEqualTo(SaveResult.Past)
        assertThat(stored().single().time).isEqualTo(LocalTime.of(8, 0))
        assertThat(actions.save(id, once(listOf(access.alex.id), time = LocalTime.of(8, 0), on = today.plusDays(1)))).isEqualTo(SaveResult.Saved)
    }

    @Test
    fun saveRefusesADraftTheFormWouldNot() = runTest {
        val access = access()
        val actions = actions(access)
        val mia = listOf(access.mia.id)
        val refused = listOf(
            once(mia, words = ""),
            once(mia, words = " \n\t "),
            once(mia, words = "a".repeat(WORDS_MAX + 1)),
            once(emptyList()),
            once(mia, on = today.plusYears(2)),
        )
        refused.forEach { assertThat(actions.save(null, it)).isEqualTo(SaveResult.Invalid) }
        assertThat(access.requests).isEmpty()
        assertThat(stored()).isEmpty()
        // A repeat with no weekday can't be built, so no draft reaches save, or repeatWords, without one.
        assertThrows(IllegalArgumentException::class.java) { Schedule.Weekly(emptySet()) }

        access.answer(TestAccess.ALEX)
        assertThat(actions.save(null, once(mia, words = "a".repeat(WORDS_MAX), on = today.plusYears(1)))).isEqualTo(SaveResult.Saved)
    }

    @Test
    fun anInvalidChangeAsksNoPinEither() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.alex.id)), access.alex.id)
        assertThat(actions.save(id, once(listOf(access.alex.id), words = ""))).isEqualTo(SaveResult.Invalid)
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun theFiftyFirstSaysTheListIsFull() = runTest {
        val access = access()
        val actions = actions(access)
        repeat(REMINDERS_MAX) { seed(once(listOf(access.alex.id)), access.alex.id) }
        access.answer(TestAccess.ALEX)
        assertThat(actions.save(null, once(listOf(access.alex.id)))).isEqualTo(SaveResult.Full)
        assertThat(stored()).hasSize(REMINDERS_MAX)
    }

    @Test
    fun deleteSaysReminderDeletedWithNoUndo() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.alex.id)), access.alex.id)
        access.answer(TestAccess.SAM)
        assertThat(actions.delete(id)).isTrue()
        assertThat(stored()).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Reminder deleted.")
        assertThat(access.toasts.actions).isEmpty()
        assertThat(access.requests.map { it.label }).containsExactly("Change this reminder")
    }

    @Test
    fun deletingOneThatHasGoneAsksNothingAndSaysNothing() = runTest {
        val access = access()
        val actions = actions(access)
        assertThat(actions.delete(404)).isTrue()
        assertThat(access.requests).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun savingAReminderThatHasGoneSaysGone() = runTest {
        val access = access()
        val actions = actions(access)
        assertThat(actions.save(404, once(listOf(access.alex.id)))).isEqualTo(SaveResult.Gone)
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun aReminderThatWentWhileThePinPadWasUpSaysGone() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.alex.id)), access.alex.id)
        val result = async { actions.save(id, once(listOf(access.alex.id), words = "Changed")) }
        awaiting("the PIN pad") { while (access.requests.isEmpty()) delay(10) }
        store.delete(id)
        access.prompt.submit(TestAccess.ALEX)
        assertThat(result.await()).isEqualTo(SaveResult.Gone)
        assertThat(stored()).isEmpty()
    }

    @Test
    fun aRefusedWhoTapNamesWhoThePersonMayAddFor() = runTest {
        val access = access()
        val actions = actions(access)
        actions.refuseOtherWho("Mia")
        assertThat(access.toasts.messages).containsExactly("Mia can add reminders for Mia only.")
        assertThat(access.toasts.urgent).containsExactly("Mia can add reminders for Mia only.")
    }

    @Test
    fun aDayBeforeTodayIsPastAndMoreThanAYearAheadIsInvalid() = runTest {
        val access = access()
        val actions = actions(access)
        access.answer(TestAccess.ALEX)
        assertThat(actions.save(null, once(listOf(access.alex.id), on = today.minusDays(1)))).isEqualTo(SaveResult.Past)
        assertThat(actions.save(null, once(listOf(access.alex.id), on = today.plusYears(1).plusDays(1)))).isEqualTo(SaveResult.Invalid)
        assertThat(stored()).isEmpty()
    }

    @Test
    fun aReminderDeletedWhileTheDeletePinPadWasUpSaysNothingAndCloses() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.alex.id)), access.alex.id)
        val result = async { actions.delete(id) }
        awaiting("the PIN pad") { while (access.requests.isEmpty()) delay(10) }
        store.delete(id)
        access.prompt.submit(TestAccess.ALEX)
        assertThat(result.await()).isTrue()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun aChildWithASessionInForceIsRefusedByNameWithNoNewPad() = runTest {
        val access = access()
        val actions = actions(access)
        val id = seed(once(listOf(access.alex.id)), access.alex.id)
        signInMia(access)
        assertThat(actions.save(id, once(listOf(access.alex.id), words = "Changed"))).isEqualTo(SaveResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change reminders they made.")
        assertThat(access.requests).hasSize(1)
        assertThat(access.control.session.value).isNull()
        assertThat(stored().single().words).isEqualTo("PE kit")
    }

    @Test
    fun aFailedOpenToastsItsLine() = runTest {
        val access = access()
        val actions = actions(access)
        actions.couldNotOpen()
        assertThat(access.toasts.urgent).containsExactly("Couldn’t open the reminder — try again.")
        assertThat(COULD_NOT_SAVE).isEqualTo("Couldn’t save — try again.")
    }
}
