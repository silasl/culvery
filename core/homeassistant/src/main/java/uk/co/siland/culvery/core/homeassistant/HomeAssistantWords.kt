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
