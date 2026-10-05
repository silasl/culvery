package uk.co.siland.culvery.core.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

/** A store whose writes fail while [fail] is set, as a full disk would; reads and the edit's decision still run. */
private class FailingWrites(private val real: DataStore<Preferences>) : DataStore<Preferences> {
    var fail = true
    override val data = real.data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        if (!fail) return real.updateData(transform)
        transform(real.data.first())
        throw IOException("No space left on device")
    }
}

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class SetupStateTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
    }

    /** A start of the app: a new DataStore over the same file, once the last one has let go of it. */
    private suspend fun start(): SetupState {
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return SetupState(setupStore(scope) { file }, household)
    }

    private val file get() = File(folder.root, "setup.preferences_pb")

    private suspend fun addAdmin(withPin: Boolean = true) {
        household.addPerson("Alex", 0xFF4CB387, Role.ADMIN, pinHash = "hash".takeIf { withPin }, salt = "salt".takeIf { withPin })
    }

    @Test
    fun aFreshInstallIsNotComplete() = runTest {
        assertThat(start().setupComplete.first()).isFalse()
    }

    @Test
    fun anInstallWithAnActiveAdminIsCompleteOnItsFirstStart() = runTest {
        addAdmin()
        assertThat(start().setupComplete.first()).isTrue()
    }

    @Test
    fun anAdminWithoutAPinDoesNotCount() = runTest {
        addAdmin(withPin = false)
        assertThat(start().setupComplete.first()).isFalse()
    }

    @Test
    fun anAdminAddedAfterTheFirstStartNeverCompletesSetup() = runTest {
        assertThat(start().setupComplete.first()).isFalse()
        // The wizard's You step makes the Admin; then the app is killed and starts again.
        addAdmin()
        assertThat(start().setupComplete.first()).isFalse()
    }

    @Test
    fun completeIsKeptAcrossARestart() = runTest {
        val first = start()
        assertThat(first.setupComplete.first()).isFalse()
        first.markComplete()
        assertThat(start().setupComplete.first()).isTrue()
    }

    @Test
    fun aCorruptSetupFileIsDecidedAgainFromTheHousehold() = runTest {
        // Not a preferences file: DataStore reads it as corrupt, and the handler replaces it with an empty one.
        file.writeText("not a preferences file")
        assertThat(start().setupComplete.first()).isFalse()
        addAdmin()
        file.writeText("not a preferences file")
        assertThat(start().setupComplete.first()).isTrue()
    }

    @Test
    fun aDecisionThatCouldNotBeStoredHoldsForEveryLaterRead() = runTest {
        val store = FailingWrites(setupStore(scope) { file })
        val state = SetupState(store, household)
        assertThat(state.setupComplete.first()).isFalse()
        // The wizard's You step makes the Admin; the app's screen then subscribes again.
        addAdmin()
        assertThat(state.setupComplete.first()).isFalse()
        store.fail = false
        state.markComplete()
        assertThat(state.setupComplete.first()).isTrue()
    }

    @Test
    fun theFurthestStepPassedIsRememberedAcrossARestart() = runTest {
        val first = start()
        assertThat(first.passedStep.first()).isNull()
        first.markPassed("household")
        assertThat(start().passedStep.first()).isEqualTo("household")
    }

    @Test
    fun welcomeIsRememberedAcrossARestart() = runTest {
        val first = start()
        assertThat(first.welcomed.first()).isFalse()
        first.markWelcomed()
        assertThat(start().welcomed.first()).isTrue()
    }
}
