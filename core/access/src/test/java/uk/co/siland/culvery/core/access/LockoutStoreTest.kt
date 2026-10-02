package uk.co.siland.culvery.core.access

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockoutStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = LockoutStore(context)
    private val t0 = 1_000_000L

    @Test
    fun fourFailuresDoNotLock() = runTest {
        repeat(4) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isNull()
    }

    @Test
    fun fifthFailureLocksForThirtySecondsThenDoubles() = runTest {
        repeat(5) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + 30_000)
        store.recordFailure(t0 + 31_000)
        assertThat(store.lockedUntil(t0 + 31_000)).isEqualTo(t0 + 31_000 + 60_000)
    }

    @Test
    fun lockExpires() = runTest {
        repeat(5) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0 + 30_000)).isNull()
    }

    @Test
    fun resetClearsFailures() = runTest {
        repeat(5) { store.recordFailure(t0) }
        store.reset()
        assertThat(store.lockedUntil(t0)).isNull()
        repeat(4) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isNull()
    }

    @Test
    fun lockoutSurvivesNewStoreInstance() = runTest {
        repeat(5) { store.recordFailure(t0) }
        assertThat(LockoutStore(context).lockedUntil(t0 + 1_000)).isEqualTo(t0 + 30_000)
    }

    @Test
    fun lockDurationIsCappedAtSixteenMinutes() = runTest {
        repeat(100) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + LockoutStore.MAX_LOCK_MS)
    }

    /** The longest lock, exactly 16 minutes away, is a real lock: the guard below is "more than", not "at least". */
    @Test
    fun tenFailuresLockForTheLongestTime() = runTest {
        repeat(10) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + LockoutStore.MAX_LOCK_MS)
    }

    /** K3: a lock more than 16 minutes away means the wall clock went back; it has expired. */
    @Test
    fun aLockFurtherAwayThanTheLongestIsExpired() = runTest {
        repeat(5) { store.recordFailure(t0) }
        val clockWentBack = t0 - 20 * 60_000L
        assertThat(store.lockedUntil(clockWentBack)).isNull()
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + 30_000)
    }
}
