package uk.co.siland.culvery.shell

import androidx.compose.runtime.Composable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.SunTimes

class FakeCapability(
    override val id: String,
    override val order: Int,
    shown: Boolean,
    private val cardList: List<HomeCard> = emptyList(),
    private val headerList: List<HeaderItem> = emptyList(),
) : Capability {
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    val shownFlow = MutableStateFlow(shown)
    override val hasTab: Flow<Boolean> = shownFlow
    override fun cards(): Flow<List<HomeCard>> = flowOf(cardList)
    override fun headerItems(): Flow<List<HeaderItem>> = flowOf(headerList)
    @Composable override fun TabContent() {}
}

/** A capability whose flows never emit, to prove one stuck capability can't block the shell. */
class NeverEmittingCapability(override val id: String, override val order: Int) : Capability {
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    override val hasTab: Flow<Boolean> = flow { awaitCancellation() }
    override fun cards(): Flow<List<HomeCard>> = flow { awaitCancellation() }
    @Composable override fun TabContent() {}
}

/** A capability whose cards() flow throws, to prove one failing capability can't crash the shell. */
class ThrowingCardsCapability(override val id: String, override val order: Int) : Capability {
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    override val hasTab: Flow<Boolean> = flowOf(false)
    override fun cards(): Flow<List<HomeCard>> = flow { throw IllegalStateException("boom") }
    @Composable override fun TabContent() {}
}

/** Returns [result] for every authorise call and, like the real one, starts a session on success unless [startsSession] is false. */
class FakeAccessControl(var result: Authorised? = null, private val startsSession: Boolean = true) : AccessControl {
    override val session = MutableStateFlow<Identified?>(null)
    val requested = mutableListOf<List<String>>()
    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? {
        requested += anyOf.toList()
        if (startsSession) result?.let { session.value = Identified(it.person, it.role) }
        return result
    }
    override fun lock() { session.value = null }

    var touches = 0
    var setupSessionsBegun = 0
    override fun beginSetupSession(person: Identified) {
        setupSessionsBegun++
        session.value = person
    }
    override fun endSetupSession() = Unit
    override fun touch() { touches++ }
}

/** A capability whose hasTab flow fails once, as a store hiccup would, then says it has a tab. */
class FlakyTabCapability(override val id: String, override val order: Int) : Capability {
    private var failures = 1
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    override val hasTab: Flow<Boolean> = flow {
        if (failures-- > 0) throw IllegalStateException("store hiccup")
        emit(true)
    }
    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())
    @Composable override fun TabContent() {}
}

/** A capability whose cards() flow fails once, as a store hiccup would, then shows [cardList]. */
class FlakyCardsCapability(override val id: String, override val order: Int, private val cardList: List<HomeCard>) : Capability {
    private var failures = 1
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    override val hasTab: Flow<Boolean> = flowOf(false)
    override fun cards(): Flow<List<HomeCard>> = flow {
        if (failures-- > 0) throw IllegalStateException("store hiccup")
        emit(cardList)
    }
    @Composable override fun TabContent() {}
}

/** A capability whose header items fail once, as a store hiccup would, then show [items]. */
class FlakyHeaderCapability(override val id: String, override val order: Int, private val items: List<HeaderItem>) : Capability {
    private var failures = 1
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    override val hasTab: Flow<Boolean> = flowOf(false)
    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())
    override fun headerItems(): Flow<List<HeaderItem>> = flow {
        if (failures-- > 0) throw IllegalStateException("store hiccup")
        emit(items)
    }
    @Composable override fun TabContent() {}
}

/** Today's sun times, settable. */
class FakeDaylight(sun: SunTimes?) : Daylight {
    val sun = MutableStateFlow(sun)
    override val today: Flow<SunTimes?> = this.sun
}

class FlakyDaylight(private val sun: SunTimes) : Daylight {
    private var failures = 1
    override val today: Flow<SunTimes?> = flow {
        if (failures-- > 0) throw IllegalStateException("no forecast for 51.5,-0.1")
        emit(sun)
    }
}
