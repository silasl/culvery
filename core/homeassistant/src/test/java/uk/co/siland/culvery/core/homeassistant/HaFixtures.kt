package uk.co.siland.culvery.core.homeassistant

/** The answers in `src/test/resources/ha`. */
internal object HaFixtures {
    fun text(name: String): String =
        checkNotNull(HaFixtures::class.java.getResourceAsStream("/ha/$name")) { "No fixture $name" }.use { it.readBytes().toString(Charsets.UTF_8) }

    val API = text("api.json")
    val STATES = text("states.json")
    val ADDED = text("ws_entities_added.json")
    val CHANGED = text("ws_entities_changed.json")
    val REMOVED = text("ws_entities_removed.json")
}
