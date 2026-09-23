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

    private suspend fun admin(name: String): Person =
        repo.addPerson(name, 0xFF4CB387, Role.ADMIN).also { repo.setPinHash(it.id, "hash-$name", "salt") }

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
}
