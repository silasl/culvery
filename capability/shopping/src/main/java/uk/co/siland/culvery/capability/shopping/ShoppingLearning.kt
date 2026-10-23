package uk.co.siland.culvery.capability.shopping

import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.textKey

const val LEARNED_MAX = 1_000
const val LEARNED_KEEP_MONTHS = 12L
const val COUNT_ONCE_PER_MS = 24 * 60 * 60_000L

/** Something the house has added before, by [key] (10 §4.7). */
data class Learned(val key: String, val text: String, val count: Int, val lastMillis: Long) {
    override fun toString() = "Learned(count=$count, lastMillis=$lastMillis)"
}

private val BY_POPULARITY = compareByDescending<Learned> { it.count }.thenByDescending { it.lastMillis }.thenBy { it.key }

/** Items starting with [typed], then items with a word that does, each by count, last use, then key; never what is on the list. */
internal fun suggest(typed: String, learned: List<Learned>, onList: Set<String>): List<Learned> {
    val t = textKey(typed)
    if (t.isEmpty()) return emptyList()
    val open = learned.filter { it.key !in onList }
    val prefix = open.filter { it.key.startsWith(t) }.sortedWith(BY_POPULARITY)
    val wordStart = open.filter { !it.key.startsWith(t) && it.key.contains(" $t") }.sortedWith(BY_POPULARITY)
    return prefix + wordStart
}

/** Everything learned that is neither on the list nor already shown as a suggestion, most added first. */
internal fun favourites(learned: List<Learned>, onList: Set<String>, suggested: Set<String>): List<Learned> =
    learned.filter { it.key !in onList && it.key !in suggested }.sortedWith(BY_POPULARITY)

/** The latest text whose first letter is upper case, else the latest. */
internal fun betterText(old: String, new: String): String {
    fun startsUpper(s: String) = s.firstOrNull { it.isLetter() }?.isUpperCase() == true
    return if (!startsUpper(new) && startsUpper(old)) old else new
}

/** What the house adds and ticks, kept for suggestions and favourites (10 §4.7). */
@Singleton
class ShoppingLearning @Inject constructor(private val store: ShoppingStore, private val clock: WallClock) {
    val learned: Flow<List<Learned>> = store.learned

    private var lastPruneMillis: Long? = null

    suspend fun added(text: String) = store.learnUse(text, clock.nowMillis())

    suspend fun ticked(text: String) = store.touchLearned(text, clock.nowMillis())

    suspend fun seen(items: List<ShoppingItem>) = store.learnSeen(items, clock.nowMillis())

    suspend fun markSeen(id: String) = store.markSeen(id, clock.nowMillis())

    suspend fun forget(key: String) = store.forgetLearned(key)

    suspend fun clear() = store.clearLearned()

    suspend fun pruneIfDue() {
        val now = clock.nowMillis()
        val last = lastPruneMillis
        if (last != null && now - last < COUNT_ONCE_PER_MS) return
        lastPruneMillis = now
        val cutoff = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).minusMonths(LEARNED_KEEP_MONTHS).toInstant()
        store.pruneUnusedBefore(cutoff.toEpochMilli())
    }
}
