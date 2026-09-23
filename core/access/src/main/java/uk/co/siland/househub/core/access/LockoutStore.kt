package uk.co.siland.househub.core.access

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Consecutive-failure counter. Kept in SharedPreferences so it survives the app being killed. */
@Singleton
class LockoutStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("lockout", Context.MODE_PRIVATE)

    fun lockedUntil(nowMillis: Long): Long? =
        prefs.getLong(KEY_UNTIL, 0L).takeIf { it > nowMillis }

    fun recordFailure(nowMillis: Long) {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val until = if (failures >= FREE_ATTEMPTS) {
            nowMillis + (BASE_LOCK_MS shl minOf(failures - FREE_ATTEMPTS, MAX_DOUBLINGS))
        } else {
            prefs.getLong(KEY_UNTIL, 0L)
        }
        prefs.edit().putInt(KEY_FAILURES, failures).putLong(KEY_UNTIL, until).commit()
    }

    fun reset() {
        prefs.edit().clear().commit()
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_DOUBLINGS = 5
        private const val KEY_FAILURES = "failures"
        private const val KEY_UNTIL = "lockedUntil"
    }
}
