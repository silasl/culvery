package uk.co.siland.culvery.core.access

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Consecutive-failure counter, in SharedPreferences so it survives the app being killed. Read once, off the main thread,
 * then kept in memory; each change is written in the background (4c K4).
 */
@Singleton
class LockoutStore internal constructor(private val open: () -> SharedPreferences, private val io: CoroutineContext) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }, Dispatchers.IO)

    private class Counts(val failures: Int, val until: Long)

    private val loading = Mutex()
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var counts: Counts? = null

    /** When the pad unlocks, or null. A lock ending more than [MAX_LOCK_MS] away means the clock went back: expired (4c K3). */
    suspend fun lockedUntil(nowMillis: Long): Long? =
        load().until.takeIf { it > nowMillis && it - nowMillis <= MAX_LOCK_MS }

    suspend fun recordFailure(nowMillis: Long) {
        val current = load()
        val failures = current.failures + 1
        val until = if (failures >= FREE_ATTEMPTS) {
            nowMillis + (BASE_LOCK_MS shl minOf(failures - FREE_ATTEMPTS, MAX_DOUBLINGS))
        } else {
            current.until
        }
        write(Counts(failures, until))
    }

    suspend fun reset() {
        load()
        write(Counts(0, 0L))
    }

    private suspend fun load(): Counts = counts ?: loading.withLock {
        counts ?: withContext(io) {
            val file = open().also { prefs = it }
            Counts(file.getInt(KEY_FAILURES, 0), file.getLong(KEY_UNTIL, 0L))
        }.also { counts = it }
    }

    private fun write(next: Counts) {
        counts = next
        checkNotNull(prefs).edit().putInt(KEY_FAILURES, next.failures).putLong(KEY_UNTIL, next.until).apply()
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_DOUBLINGS = 5

        /** The longest lock: 16 minutes. */
        const val MAX_LOCK_MS = BASE_LOCK_MS shl MAX_DOUBLINGS

        private const val FILE = "lockout"
        private const val KEY_FAILURES = "failures"
        private const val KEY_UNTIL = "lockedUntil"
    }
}
