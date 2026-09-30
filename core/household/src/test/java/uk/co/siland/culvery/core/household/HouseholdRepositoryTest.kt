package uk.co.siland.culvery.core.household

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

@RunWith(AndroidJUnit4::class)
class HouseholdRepositoryTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var repo: HouseholdRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HouseholdRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private var nextColour = 0xFF101010L

    private suspend fun admin(name: String): Person =
        repo.addPerson(name, nextColour++, Role.ADMIN).also { repo.setPinHash(it.id, "hash-$name", "salt") }

    @Test
    fun addedPeopleAppearInInsertionOrder() = runTest {
        repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        assertThat(repo.people.first().map { it.name }).containsExactly("Alex", "Sam").inOrder()
    }

    @Test
    fun namesAreTrimmedAndBlankRejected() = runTest {
        assertThat(repo.addPerson("  Mia ", 0xFFE07BA8, Role.CHILD).name).isEqualTo("Mia")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.addPerson("   ", 0xFF000000, Role.CHILD) }
        }
    }

    @Test
    fun peopleWithFamilyPutsFamilyFirst() = runTest {
        repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        assertThat(repo.peopleWithFamily.first().map { it.name }).containsExactly("Family", "Alex").inOrder()
    }

    @Test
    fun personLooksUpFamilyAndRealPeople() = runTest {
        val alex = repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        assertThat(repo.person(PersonId.FAMILY)).isEqualTo(Person.Family)
        assertThat(repo.person(alex.id)).isEqualTo(alex)
        assertThat(repo.person(PersonId("missing"))).isNull()
    }

    @Test
    fun newPersonHasRoleAndNoPin() = runTest {
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        assertThat(repo.credential(mia.id)).isEqualTo(Credential(mia.id, Role.CHILD, null, null))
    }

    @Test
    fun updateChangesNameAndColourButKeepsCredential() = runTest {
        val alex = admin("Alex")
        repo.updatePerson(alex.copy(name = "Alexandra", color = 0xFF000000))
        assertThat(repo.person(alex.id)).isEqualTo(Person(alex.id, "Alexandra", 0xFF000000))
        assertThat(repo.credential(alex.id)?.pinHash).isEqualTo("hash-Alex")
    }

    @Test
    fun familyCannotBeUpdatedOrRemoved() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.updatePerson(Person.Family.copy(name = "Us")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.removePerson(PersonId.FAMILY) }
        }
    }

    @Test
    fun removingAPersonRemovesTheirCredential() = runTest {
        admin("Alex")
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        repo.setPinHash(mia.id, "h", "s")
        repo.removePerson(mia.id)
        assertThat(repo.credentials().map { it.personId }).doesNotContain(mia.id)
    }

    @Test
    fun lastAdminCannotBeDemoted() = runTest {
        val alex = admin("Alex")
        assertThrows(LastAdminException::class.java) { runBlocking { repo.setRole(alex.id, Role.ADULT) } }
    }

    @Test
    fun lastAdminCannotBeRemoved() = runTest {
        val alex = admin("Alex")
        assertThrows(LastAdminException::class.java) { runBlocking { repo.removePerson(alex.id) } }
    }

    @Test
    fun lastAdminCannotHavePinCleared() = runTest {
        val alex = admin("Alex")
        assertThrows(LastAdminException::class.java) { runBlocking { repo.clearPin(alex.id) } }
    }

    @Test
    fun oneOfTwoAdminsCanBeDemotedOrRemoved() = runTest {
        admin("Alex")
        val sam = admin("Sam")
        repo.setRole(sam.id, Role.ADULT)
        assertThat(repo.credential(sam.id)?.role).isEqualTo(Role.ADULT)
        repo.removePerson(sam.id)
        assertThat(repo.person(sam.id)).isNull()
    }

    @Test
    fun adminWithoutPinDoesNotCountAsLastAdmin() = runTest {
        val alex = admin("Alex")
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADMIN)
        assertThrows(LastAdminException::class.java) { runBlocking { repo.removePerson(alex.id) } }
    }

    @Test
    fun pinHashNeedsAnExistingPerson() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.setPinHash(PersonId("nobody"), "h", "s") }
        }
    }

    @Test
    fun locationRoundTrips() = runTest {
        assertThat(repo.location.first()).isNull()
        val home = HomeLocation("Balcombe", 51.06, -0.13, "Europe/London")
        repo.setLocation(home)
        assertThat(repo.location.first()).isEqualTo(home)
    }

    @Test
    fun aNameInUseIsRefusedWhateverItsCaseOrSpaces() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        val refused = assertThrows(DuplicateNameException::class.java) {
            runBlocking { repo.addPerson("  sAM ", 0xFFE07BA8, Role.CHILD) }
        }
        assertThat(refused.name).isEqualTo("sAM")
        assertThat(refused.message).doesNotContain("sAM")
        assertThat(repo.people.first().map { it.name }).containsExactly("Sam")
    }

    @Test
    fun nobodyCanBeCalledFamily() = runTest {
        assertThrows(DuplicateNameException::class.java) { runBlocking { repo.addPerson("family", 0xFF5B9BE0, Role.ADULT) } }
    }

    @Test
    fun aColourInUseIsRefused() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        assertThrows(ColourInUseException::class.java) { runBlocking { repo.addPerson("Mia", 0xFF5B9BE0, Role.CHILD) } }
    }

    @Test
    fun renamingAndRecolouringFollowTheSameRules() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        assertThrows(DuplicateNameException::class.java) { runBlocking { repo.updatePerson(mia.copy(name = "SAM")) } }
        assertThrows(ColourInUseException::class.java) { runBlocking { repo.updatePerson(mia.copy(color = 0xFF5B9BE0)) } }
        repo.updatePerson(mia.copy(name = "MIA"))
        assertThat(repo.person(mia.id)?.name).isEqualTo("MIA")
    }

    @Test
    fun aNinthPersonIsRefused() = runTest {
        repeat(MAX_PEOPLE) { repo.addPerson("P$it", 0xFF100000L + it, Role.ADULT) }
        assertThrows(HouseholdFullException::class.java) { runBlocking { repo.addPerson("P8", 0xFF200000L, Role.ADULT) } }
        assertThat(repo.people.first()).hasSize(MAX_PEOPLE)
    }

    @Test
    fun aPersonAddedWithAPinHasItFromTheStart() = runTest {
        assertThat(repo.hasActiveAdmin.first()).isFalse()
        val alex = repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN, pinHash = "h", salt = "s")
        assertThat(repo.credential(alex.id)).isEqualTo(Credential(alex.id, Role.ADMIN, "h", "s"))
        assertThat(repo.hasActiveAdmin.first()).isTrue()
        assertThat(repo.members.first()).containsExactly(Member(alex, Role.ADMIN, hasPin = true))
    }

    @Test
    fun familyHasNoRoleOrPin() = runTest {
        // Today these fail only as an unknown person; the refusal must name Family.
        val refusals = listOf(
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setRole(PersonId.FAMILY, Role.ADULT) } },
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setPinHash(PersonId.FAMILY, "h", "s") } },
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.clearPin(PersonId.FAMILY) } },
        )
        assertThat(refusals.map { it.message }.toSet()).containsExactly("Family has no role or PIN")
    }

    @Test
    fun aMemberEditChangesNameColourRoleAndPinTogether() = runTest {
        admin("Alex")
        val sam = repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        repo.updateMember(sam.id, "Samuel", 0xFF9C7CE3, Role.ADMIN, PinChange.Set("h", "s"))
        assertThat(repo.member(sam.id)).isEqualTo(Member(Person(sam.id, "Samuel", 0xFF9C7CE3), Role.ADMIN, hasPin = true))
        repo.updateMember(sam.id, "Samuel", 0xFF9C7CE3, Role.ADULT, PinChange.Remove)
        assertThat(repo.credential(sam.id)).isEqualTo(Credential(sam.id, Role.ADULT, null, null))
    }

    @Test
    fun aRefusedMemberEditChangesNothing() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        assertThrows(ColourInUseException::class.java) {
            runBlocking { repo.updateMember(mia.id, "Amelia", 0xFF5B9BE0, Role.ADULT, PinChange.Set("h", "s")) }
        }
        assertThat(repo.member(mia.id)).isEqualTo(Member(mia, Role.CHILD, hasPin = false))
    }

    @Test
    fun theLastAdminIsKeptThroughAMemberEdit() = runTest {
        val alex = admin("Alex")
        assertThrows(LastAdminException::class.java) {
            runBlocking { repo.updateMember(alex.id, "Alex", alex.color, Role.ADULT, PinChange.Keep) }
        }
        assertThrows(LastAdminException::class.java) {
            runBlocking { repo.updateMember(alex.id, "Alex", alex.color, Role.ADMIN, PinChange.Remove) }
        }
        assertThat(repo.member(alex.id)?.isActiveAdmin).isTrue()
    }

    private val refuseEveryPin = PinGuard { throw IllegalStateException("guard ran") }

    @Test
    fun aMemberEditRefusesADuplicateNameAColourInUseAndFamily() = runTest {
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        assertThrows(DuplicateNameException::class.java) {
            runBlocking { repo.updateMember(mia.id, " sam", mia.color, Role.CHILD, PinChange.Keep) }
        }
        assertThrows(DuplicateNameException::class.java) {
            runBlocking { repo.updateMember(mia.id, "FAMILY", mia.color, Role.CHILD, PinChange.Keep) }
        }
        assertThrows(ColourInUseException::class.java) {
            runBlocking { repo.updateMember(mia.id, "Mia", 0xFF5B9BE0, Role.CHILD, PinChange.Keep) }
        }
        assertThat(repo.member(mia.id)).isEqualTo(Member(mia, Role.CHILD, hasPin = false))
    }

    @Test
    fun familyCannotBeEditedAsAMember() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.updateMember(PersonId.FAMILY, "Family", 0xFF5B9BE0, Role.ADULT, PinChange.Keep) }
        }
    }

    @Test
    fun aPinNeedsBothItsHashAndItsSalt() = runTest {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT, pinHash = "h") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT, salt = "s") } }
        assertThat(repo.people.first()).isEmpty()
    }

    @Test
    fun aPinGuardSeesOnlyOthersAndItsRefusalChangesNothing() = runTest {
        val alex = admin("Alex")
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        var seen: List<PersonId> = emptyList()
        val spy = PinGuard { others -> seen = others.map { it.personId } }
        repo.updateMember(mia.id, "Mia", mia.color, Role.CHILD, PinChange.Set("h", "s", spy))
        assertThat(seen).containsExactly(alex.id)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.updateMember(mia.id, "Mia", mia.color, Role.CHILD, PinChange.Set("h2", "s2", refuseEveryPin)) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT, "h", "s", refuseEveryPin) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.setPinHash(mia.id, "h3", "s3", refuseEveryPin) }
        }
        assertThat(repo.credential(mia.id)?.pinHash).isEqualTo("h")
        assertThat(repo.people.first().map { it.name }).containsExactly("Alex", "Mia")
    }
}
