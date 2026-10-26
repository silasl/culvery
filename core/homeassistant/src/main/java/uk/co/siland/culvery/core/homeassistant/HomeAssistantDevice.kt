package uk.co.siland.culvery.core.homeassistant

import kotlinx.coroutines.flow.Flow

/** A kind of device Settings › Other integrations offers a picker for (11 §4.1), bound by its provider. */
interface HomeAssistantDevice {
    val order: Int
    /** The picker's label: "Hoover", "Thermostat". */
    val title: String
    /** The entity domain it lists: "vacuum", "climate". */
    val domain: String
    val noneFound: String
    val chosen: Flow<String?>
    /** What Settings watches for it: the chosen entity and any it reads beside it. */
    val watched: Flow<Set<String>>
    /** Under the picker: its state or what's wrong; null with nothing chosen. */
    val line: Flow<String?>
    suspend fun choose(entityId: String?)
}
