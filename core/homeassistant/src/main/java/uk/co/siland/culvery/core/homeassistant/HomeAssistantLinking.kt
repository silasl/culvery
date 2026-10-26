package uk.co.siland.culvery.core.homeassistant

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.mayManageConnections

/** What Check made of an address and a token. */
sealed interface CheckOutcome {
    data object Linked : CheckOutcome

    /** The Admin PIN was refused or cancelled: nothing was saved and there is nothing to say. */
    data object PinRefused : CheckOutcome

    /** [line] says what to fix; [field] is where it shows. */
    data class Failed(val line: String, val field: CheckField) : CheckOutcome
}

enum class CheckField { ADDRESS, TOKEN, NONE }

/** An entity a picker offers; [toString] names neither its id nor its name. */
class EntityChoice(val id: String, val name: String) {
    override fun toString() = "EntityChoice"
}

/**
 * Check on Settings › Other integrations (11 §4.1): the address by the rule, `GET /api/` with the token, then a fresh
 * Admin PIN before a different link is saved; the first of each kind found alone is chosen.
 */
@Singleton
class HomeAssistantLinking internal constructor(
    private val ha: HomeAssistant,
    private val settings: HomeAssistantSettings,
    private val access: AccessControl,
    private val devices: Set<HomeAssistantDevice>,
    private val url: (String) -> HttpUrl?,
) {
    @Inject
    constructor(ha: HomeAssistant, settings: HomeAssistantSettings, access: AccessControl, devices: Set<@JvmSuppressWildcards HomeAssistantDevice>) :
        this(ha, settings, access, devices, ::homeAssistantUrl)

    /** A blank [token] keeps the saved one while the address is the saved one. A refused address sends nothing. */
    suspend fun check(address: String, token: String): CheckOutcome {
        val base = url(address) ?: return CheckOutcome.Failed(ADDRESS_REFUSED, CheckField.ADDRESS)
        val saved = settings.link.first()
        val typed = token.trim()
        val secret = typed.ifEmpty { saved?.takeIf { it.baseUrl == base }?.token }
            ?: return CheckOutcome.Failed(PASTE_A_TOKEN, CheckField.TOKEN)
        val link = HomeAssistantLink(base, secret)
        try {
            ha.check(link)
        } catch (e: HomeAssistantException) {
            return e.problem.failure()
        }
        if (link == saved) return CheckOutcome.Linked
        if (!access.mayManageConnections()) return CheckOutcome.PinRefused
        settings.save(link)
        pickTheOnlyOnes(link)
        return CheckOutcome.Linked
    }

    /** Every [domain] entity Home Assistant lists, by name; throws [HomeAssistantException]. */
    suspend fun entities(domain: String): List<EntityChoice> =
        ha.states().filter { it.domain == domain }.map { EntityChoice(it.entityId, it.friendlyName ?: it.entityId) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    // The cache follows the saved link a moment later; reading before it does would ask Home Assistant with the old one.
    private suspend fun pickTheOnlyOnes(link: HomeAssistantLink) {
        withTimeoutOrNull(LINK_SETTLES_MS) { ha.link.first { it == link } } ?: return
        val unchosen = devices.filter { it.chosen.first() == null }
        if (unchosen.isEmpty()) return
        val states = try {
            ha.states()
        } catch (e: HomeAssistantException) {
            return
        }
        for (device in unchosen) {
            states.singleOrNull { it.domain == device.domain }?.let { device.choose(it.entityId) }
        }
    }

    private fun HaProblem.failure() = when (this) {
        HaProblem.TOKEN_REFUSED -> CheckOutcome.Failed(TOKEN_NOT_ACCEPTED, CheckField.TOKEN)
        HaProblem.NOT_HOME_ASSISTANT -> CheckOutcome.Failed(NOT_HOME_ASSISTANT_LINE, CheckField.ADDRESS)
        else -> CheckOutcome.Failed(problemLine(this), CheckField.NONE)
    }

    private companion object {
        const val LINK_SETTLES_MS = 5_000L
    }
}
