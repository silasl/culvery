package uk.co.siland.househub.shell

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.househub.core.access.AccessControl
import uk.co.siland.househub.core.access.Authorised
import uk.co.siland.househub.core.access.Identified
import uk.co.siland.househub.core.plugin.Capability
import uk.co.siland.househub.core.plugin.HomeCard

class FakeCapability(
    override val id: String,
    override val order: Int,
    shown: Boolean,
    private val cardList: List<HomeCard> = emptyList(),
) : Capability {
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    val shownFlow = MutableStateFlow(shown)
    override val hasTab: Flow<Boolean> = shownFlow
    override fun cards(): Flow<List<HomeCard>> = flowOf(cardList)
    @Composable override fun TabContent() {}
}

/** Returns [result] for every authorise call and, like the real one, starts a session on success. */
class FakeAccessControl(var result: Authorised? = null) : AccessControl {
    override val session = MutableStateFlow<Identified?>(null)
    val requested = mutableListOf<List<String>>()
    var touches = 0
    override suspend fun authorise(vararg anyOf: String): Authorised? {
        requested += anyOf.toList()
        result?.let { session.value = Identified(it.person, it.role) }
        return result
    }
    override fun touch() { touches++ }
    override fun lock() { session.value = null }
}
