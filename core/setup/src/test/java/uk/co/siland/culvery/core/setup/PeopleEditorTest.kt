package uk.co.siland.culvery.core.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.ui.PersonPalette

// Robolectric for Room and android.util.Log.
@RunWith(AndroidJUnit4::class)
class PeopleEditorTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private lateinit var editor: PeopleEditor
    private lateinit var alex: Person

    @Before
    fun setUp() {
        ShadowLog.clear()
        db = householdDb()
        household = HouseholdRepository(db)
    }

    @After
    fun tearDown() = db.close()

    /** Alex, the Admin, has Settings open: signed in with PIN 1234. */
    private suspend fun TestScope.start() {
        access = testAccess(household)
        editor = PeopleEditor(household, access.pins, access.control, access.toasts)
        alex = access.addAdmin()
        access.answer("1234")
        access.control.authorise(CorePermissions.SETTINGS_MANAGE)
        access.requests.clear()
    }

    private fun draft(name: String, colour: Int, role: Role = Role.ADULT, pin: String? = null) =
        PersonDraft(name, PersonPalette.colors[colour], role, pin)

    private suspend fun sam(pin: String? = null, role: Role = Role.ADULT): Person =
        access.pins.addPerson("Sam", PersonPalette.colors[1], role, pin)

    @Test
    fun addingSomeoneAsksForAFreshPinAndAddsThem() = runTest {
        start()
        access.answer("1234")
        assertThat(editor.add(draft("Sam", 1))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.requests.map { it.label }).containsExactly("Manage people")
        assertThat(household.members.first().map { it.person.name }).containsExactly("Alex", "Sam").inOrder()
    }

    @Test
    fun renamingAndRecolouringNeedOnlyTheOpenSession() = runTest {
        start()
        val sam = sam()
        assertThat(editor.save(sam.id, draft("Samuel", 3))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.requests).isEmpty()
        assertThat(household.person(sam.id)).isEqualTo(Person(sam.id, "Samuel", PersonPalette.colors[3]))
    }

    @Test
    fun aRoleChangeOrAnyPinChangeAsksForAFreshPin() = runTest {
        start()
        val sam = sam()
        access.answer("1234", "1234", "1234")
        assertThat(editor.save(sam.id, draft("Sam", 1, Role.CHILD))).isEqualTo(PeopleOutcome.Done)
        assertThat(editor.save(sam.id, draft("Sam", 1, Role.CHILD, pin = "2468"))).isEqualTo(PeopleOutcome.Done)
        assertThat(household.member(sam.id)?.hasPin).isTrue()
        assertThat(editor.save(sam.id, draft("Sam", 1, Role.CHILD).copy(removePin = true))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.requests.map { it.label }).containsExactly("Manage people", "Manage people", "Manage people")
        assertThat(household.member(sam.id)).isEqualTo(Member(sam, Role.CHILD, hasPin = false))
    }

    @Test
    fun aNameInUseIsRefusedAndNothingChanges() = runTest {
        start()
        val sam = sam()
        assertThat(editor.save(sam.id, draft("ALEX", 1))).isEqualTo(PeopleOutcome.Refused("Someone is already called ALEX."))
        assertThat(household.person(sam.id)?.name).isEqualTo("Sam")
    }

    @Test
    fun aTakenColourIsRefused() = runTest {
        start()
        val sam = sam()
        assertThat(editor.save(sam.id, draft("Sam", 0))).isEqualTo(PeopleOutcome.Refused("That colour is taken."))
    }

    @Test
    fun aTakenPinIsRefusedAndNobodyIsAdded() = runTest {
        start()
        access.answer("1234")
        assertThat(editor.add(draft("Sam", 1, pin = "1234"))).isEqualTo(PeopleOutcome.Refused("That PIN is taken — choose another."))
        assertThat(household.members.first()).hasSize(1)
    }

    @Test
    fun anAdminWithoutAPinIsRefusedBeforeAnyPinPad() = runTest {
        start()
        assertThat(editor.add(draft("Sam", 1, Role.ADMIN))).isEqualTo(PeopleOutcome.Refused("An Admin needs a PIN."))
        val sam = sam(pin = "2468", role = Role.ADMIN)
        assertThat(editor.save(sam.id, draft("Sam", 1, Role.ADMIN).copy(removePin = true))).isEqualTo(PeopleOutcome.Refused("An Admin needs a PIN."))
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun theLastAdminCannotBeDemotedRemovedOrLoseTheirPin() = runTest {
        start()
        access.answer("1234", "1234")
        val refused = PeopleOutcome.Refused("Culvery needs at least one Admin with a PIN.")
        assertThat(editor.save(alex.id, draft("Alex", 0, Role.ADULT).copy(removePin = true))).isEqualTo(refused)
        assertThat(editor.remove(alex.id)).isEqualTo(refused)
        assertThat(household.member(alex.id)?.isActiveAdmin).isTrue()
    }

    @Test
    fun changingTheSignedInPersonsPinSignsThemOut() = runTest {
        start()
        sam(pin = "2468", role = Role.ADMIN)
        access.answer("1234")
        assertThat(editor.save(alex.id, draft("Alex", 0, Role.ADMIN, pin = "4321"))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun changingSomeoneElsesRoleKeepsYouSignedIn() = runTest {
        start()
        val sam = sam()
        access.answer("1234")
        editor.save(sam.id, draft("Sam", 1, Role.CHILD))
        assertThat(access.control.session.value?.person?.id).isEqualTo(alex.id)
    }

    @Test
    fun renamingTheSignedInPersonKeepsThemSignedIn() = runTest {
        start()
        assertThat(editor.save(alex.id, draft("Alexandra", 0, Role.ADMIN))).isEqualTo(PeopleOutcome.Done)
        assertThat(access.control.session.value?.person?.id).isEqualTo(alex.id)
    }

    @Test
    fun removingSomeoneSaysSo() = runTest {
        start()
        val sam = sam()
        access.answer("1234")
        assertThat(editor.remove(sam.id)).isEqualTo(PeopleOutcome.Done)
        assertThat(access.toasts.messages).containsExactly("Sam removed")
        assertThat(household.members.first().map { it.person.name }).containsExactly("Alex")
    }

    @Test
    fun removingYourselfSignsYouOut() = runTest {
        start()
        sam(pin = "2468", role = Role.ADMIN)
        access.answer("1234")
        assertThat(editor.remove(alex.id)).isEqualTo(PeopleOutcome.Done)
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun aCancelledPinChangesNothing() = runTest {
        start()
        access.answer(null)
        assertThat(editor.add(draft("Sam", 1))).isEqualTo(PeopleOutcome.Cancelled)
        assertThat(household.members.first()).hasSize(1)
    }

    @Test
    fun aStoreFailureSaysCouldNotSaveAndLogsNoNameOrPin() = runTest {
        start()
        val sam = sam(pin = "2468")
        // A closed database cancels Room's scope (a CancellationException, rightly rethrown), so break the table instead.
        db.openHelper.writableDatabase.execSQL("DROP TABLE person")
        assertThat(editor.save(sam.id, draft("Samantha", 1))).isEqualTo(PeopleOutcome.Refused(COULD_NOT_SAVE))
        assertNoSecretsLogged("People", listOf("Sam", "2468", "1234"))
    }
}
