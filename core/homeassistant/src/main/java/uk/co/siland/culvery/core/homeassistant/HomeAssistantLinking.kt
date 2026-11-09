package uk.co.siland.culvery.core.homeassistant

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.mayManageConnections

private const val TAG = "HomeAssistantLinking"

/** What Check made of an address and a token. */
sealed interface CheckOutcome {
    data object Linked : CheckOutcome

    /** The Admin PIN was refused or cancelled: nothing was saved and there is nothing to say. */
    data object PinRefused : CheckOutcome

    /** [line] says what to fix. */
    data class Failed(val line: String) : CheckOutcome
}

/** An entity a picker offers; [toString] names neither its id nor its name. */
class EntityChoice(val id: String, val name: String) {
    override fun toString() = "EntityChoice"
}

/**
 * Check on Settings › Home Assistant (11 §4.1; Plan 20 N1). Over http: the address by the rule, `GET /api/` with the
 * token, then a fresh Admin PIN before a different link is saved. Over https: the certificate is read first without the
 * token; a key other than the saved pin asks the fresh Admin PIN, showing its fingerprint, before the token is sent, and
 * the link is then saved with that pin. Once a pin is stored, an http address is refused. The first of each kind found
 * alone is chosen.
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

    /**
     * A blank [token] keeps the saved one for the saved address, for the same host moving from http to https, and for any
     * https address presenting the saved pin's key. A refused address sends nothing.
     */
    suspend fun check(address: String, token: String): CheckOutcome {
        val base = url(address) ?: return CheckOutcome.Failed(ADDRESS_REFUSED)
        if (!base.isHttps && settings.storedPin.first() != null) return CheckOutcome.Failed(USE_HTTPS)
        val saved = settings.link.first()
        val typed = token.trim()
        // A pasted zero-width space or line break can't go in a header; refusing it here sends nothing.
        if (typed.any { it !in '!'..'~' }) return CheckOutcome.Failed(TOKEN_CANT_BE_SENT)
        val kept = typed.ifEmpty { saved?.takeIf { it.baseUrl == base || it.movesToHttps(base) }?.token }
        if (!base.isHttps) {
            val link = HomeAssistantLink(base, kept ?: return CheckOutcome.Failed(PASTE_A_TOKEN), pin = null)
            return checkAndSave(link, saved, trusted = false)
        }
        if (kept == null && saved?.pin == null) return CheckOutcome.Failed(PASTE_A_TOKEN)
        val pin = try {
            ha.certificate(base)
        } catch (e: HomeAssistantException) {
            return e.problem.failure()
        }
        val secret = kept ?: saved?.takeIf { it.pin == pin }?.token ?: return CheckOutcome.Failed(PASTE_A_TOKEN)
        val known = pin == saved?.pin
        if (!known) {
            val detail = "$CERTIFICATE_FINGERPRINT\n${fingerprint(pin)}"
            access.authorise(CorePermissions.CONNECTIONS_MANAGE, label = TRUST_HOME_ASSISTANT, detail = detail) ?: return CheckOutcome.PinRefused
        }
        return checkAndSave(HomeAssistantLink(base, secret, pin), saved, trusted = !known)
    }

    /** Asks a fresh Admin PIN, then removes the saved address, token and pin; false when refused. Throws `IOException` if the write fails. */
    suspend fun forget(): Boolean {
        access.authorise(CorePermissions.CONNECTIONS_MANAGE, label = FORGET_HOME_ASSISTANT) ?: return false
        settings.forget()
        return true
    }

    // [trusted]: the Admin PIN was given for this link already, so saving asks no second one.
    private suspend fun checkAndSave(link: HomeAssistantLink, saved: HomeAssistantLink?, trusted: Boolean): CheckOutcome {
        try {
            ha.check(link)
        } catch (e: HomeAssistantException) {
            return e.problem.failure()
        }
        if (link == saved) return CheckOutcome.Linked
        if (!trusted && !access.mayManageConnections()) return CheckOutcome.PinRefused
        try {
            settings.save(link)
        } catch (e: PinStoredException) {
            return CheckOutcome.Failed(USE_HTTPS)
        }
        pickTheOnlyOnes(link)
        return CheckOutcome.Linked
    }

    private fun HomeAssistantLink.movesToHttps(to: HttpUrl) = !baseUrl.isHttps && to.isHttps && baseUrl.host == to.host

    /** Each of [domains]' entities by name, from one read of Home Assistant; throws [HomeAssistantException]. */
    suspend fun entities(domains: Set<String>): Map<String, List<EntityChoice>> {
        val states = ha.states()
        return domains.associateWith { domain ->
            states.filter { it.domain == domain }.map { EntityChoice(it.entityId, it.friendlyName ?: it.entityId) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

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
            val only = states.singleOrNull { it.domain == device.domain } ?: continue
            // Best-effort: the link is saved, so a pick that can't be written is left for the household to choose.
            try {
                device.choose(only.entityId)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't save an automatic choice (${e::class.simpleName})")
            }
        }
    }

    private fun HaProblem.failure() = when (this) {
        HaProblem.TOKEN_REFUSED -> CheckOutcome.Failed(TOKEN_NOT_ACCEPTED)
        HaProblem.NOT_HOME_ASSISTANT -> CheckOutcome.Failed(NOT_HOME_ASSISTANT_LINE)
        HaProblem.CERTIFICATE_CHANGED -> CheckOutcome.Failed(CERTIFICATE_CHANGED_ON_PAGE)
        else -> CheckOutcome.Failed(problemLine(this))
    }

    private companion object {
        const val LINK_SETTLES_MS = 5_000L
    }
}
