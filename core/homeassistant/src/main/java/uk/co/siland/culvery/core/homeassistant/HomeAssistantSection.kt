package uk.co.siland.culvery.core.homeassistant

import androidx.compose.runtime.Composable

/** A block a provider adds to Settings › Other integrations, below the device pickers, while a link is saved (12 §3). */
interface HomeAssistantSection {
    /** Blocks run top to bottom by [order]. */
    val order: Int

    @Composable
    fun Content()
}
