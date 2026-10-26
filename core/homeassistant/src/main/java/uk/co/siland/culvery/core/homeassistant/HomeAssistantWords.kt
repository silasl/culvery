package uk.co.siland.culvery.core.homeassistant

/** A call with no link saved. */
const val NOT_LINKED = "Home Assistant isn’t linked. Link it in Settings › Other integrations."

/** What the row and Settings say for [problem] (11 §6). */
fun problemLine(problem: HaProblem): String = when (problem) {
    HaProblem.UNREACHABLE, HaProblem.NOT_HOME_ASSISTANT, HaProblem.REFUSED -> "Can’t reach Home Assistant. Check the NAS is on."
    HaProblem.OFFLINE -> "The tablet is offline. Check the Wi-Fi."
    HaProblem.TOKEN_REFUSED -> "Home Assistant no longer accepts Culvery’s token. Add a new one in Settings › Other integrations."
    HaProblem.BLOCKED -> "Home Assistant has blocked the tablet. Unblock its address in Home Assistant."
}

/** Settings › Other integrations (11 §4.1, §6). */
const val OTHER_INTEGRATIONS = "Other integrations"
const val OTHER_INTEGRATIONS_LINE = "Devices Culvery reaches through Home Assistant."
const val HOME_ASSISTANT = "Home Assistant"
const val NOT_LINKED_YET = "Not linked yet. Enter Home Assistant’s address and a token for Culvery, then Check."
const val LINKED = "Linked."
const val HA_ADDRESS = "Home Assistant’s address"
// Joined so InternetHttpsTest, which refuses a quoted plain-HTTP URL, sees an example to type, not a URL to fetch.
const val HA_ADDRESS_EXAMPLE = "http" + "://192.168.1.20:8123"
const val HA_TOKEN = "Token for Culvery"
const val HA_TOKEN_PLACEHOLDER = "Paste the token here"
const val HA_TOKEN_SAVED = "Saved. Paste a new token to replace it."
const val HA_HELP = "Give the NAS a fixed address on the router. Then sign in to Home Assistant as the Culvery user (not an administrator), " +
    "open Profile › Security › Long-lived access tokens › Create token, and paste the token here."
const val HA_CHECK = "Check"
const val HA_NONE = "None"

const val ADDRESS_REFUSED = "Use an address on the home network, like http://192.168.1.20:8123, or one that starts https://."
const val PASTE_A_TOKEN = "Paste a token for Culvery first."
const val TOKEN_NOT_ACCEPTED = "Home Assistant didn’t accept that token. Create a new one and paste it here."
const val NOT_HOME_ASSISTANT_LINE = "Nothing at that address answered as Home Assistant. Check the address."
