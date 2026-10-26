package uk.co.siland.culvery.core.homeassistant

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uk.co.siland.culvery.core.plugin.isHomeNetworkHost

/** The id of Home Assistant as a source of devices (11 §4.1). */
const val HOME_ASSISTANT_ID = "home.homeassistant"

/** Home Assistant's own port, assumed when none is typed (11 §4.1). */
const val HOME_ASSISTANT_PORT = 8123

/** The saved address and long-lived token; [toString] names neither. */
data class HomeAssistantLink(val baseUrl: HttpUrl, val token: String) {
    override fun toString() = "HomeAssistantLink"
}

/**
 * A typed address as the URL Culvery will contact, or null (11 §4.1). No scheme means `http`; no port means 8123; `http`
 * only to a home-network host, so the token never leaves the home Wi-Fi in clear; `https` to any host. No user info,
 * path, query or fragment.
 */
fun homeAssistantUrl(typed: String): HttpUrl? {
    val text = typed.trim()
    val scheme = if ("://" in text) text.substringBefore("://").lowercase() else "http"
    if (scheme != "http" && scheme != "https") return null
    val rest = text.substringAfter("://", text)
    if (rest.any { it == '@' || it == '?' || it == '#' || it == '\\' || it.isWhitespace() }) return null
    val authority = rest.substringBefore('/')
    val path = rest.substring(authority.length)
    if (authority.isEmpty() || authority.endsWith(':') || (path.isNotEmpty() && path != "/")) return null
    // Read before parsing: OkHttp drops a typed port that is the scheme's default (80, 443).
    val port = if (':' in authority) "" else ":$HOME_ASSISTANT_PORT"
    val url = "$scheme://$authority$port/".toHttpUrlOrNull() ?: return null
    return url.takeIf { it.encodedPath == "/" && (scheme == "https" || isHomeNetworkHost(it.host)) }
}
