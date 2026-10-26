package uk.co.siland.culvery.core.homeassistant

/** The kinds of call to Home Assistant; the label is all a log line may say of one (11 §6). */
enum class HaCall(val label: String) {
    CHECK("check"),
    STATES("states"),
    STATE("state"),
    SERVICE("service"),
    SOCKET("socket"),
}
