package uk.co.siland.culvery.core.access

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockoutStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = LockoutStore(context)
    private val t0 = 1_000_000L

    @Test
    fun fourFailuresDoNotLock() {
        repeat(4) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isNull()
    }

    @Test
    fun fifthFailureLocksForThirtySecondsThenDoubles() {
        repeat(5) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + 30_000)
        store.recordFailure(t0 + 31_000)
        assertThat(store.lockedUntil(t0 + 31_000)).isEqualTo(t0 + 31_000 + 60_000)
    }

    @Test
    fun lockExpires() {
        repeat(5) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0 + 30_000)).isNull()
    }

    @Test
    fun resetClearsFailures() {
        repeat(5) { store.recordFailure(t0) }
        store.reset()
        assertThat(store.lockedUntil(t0)).isNull()
        repeat(4) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isNull()
    }

    @Test
    fun lockoutSurvivesNewStoreInstance() {
        repeat(5) { store.recordFailure(t0) }
        assertThat(LockoutStore(context).lockedUntil(t0 + 1_000)).isEqualTo(t0 + 30_000)
    }

    @Test
    fun lockDurationIsCappedAtSixteenMinutes() {
        repeat(100) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + 30_000L * 32)
    }
}
