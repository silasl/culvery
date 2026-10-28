package uk.co.siland.culvery.core.homeassistant

/** The answers in `src/test/resources/ha`. */
internal object HaFixtures {
    fun text(name: String): String =
        checkNotNull(HaFixtures::class.java.getResourceAsStream("/ha/$name")) { "No fixture $name" }.use { it.readBytes().toString(Charsets.UTF_8) }

    val API = text("api.json")
    val STATES = text("states.json")
    val ADDED = text("ws_entities_added.json")
    val AUTH_REQUIRED = text("ws_auth_required.json")
    val AUTH_OK = text("ws_auth_ok.json")
    val AUTH_INVALID = text("ws_auth_invalid.json")
    val CHANGED = text("ws_entities_changed.json")
    val REMOVED = text("ws_entities_removed.json")
    val PLUG_STATES = text("plug_states.json")
    val REGISTRY = text("registry_display.json")
}
