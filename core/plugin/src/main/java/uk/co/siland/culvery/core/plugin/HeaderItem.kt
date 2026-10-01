package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable

/** An item a capability puts on the right of Home's header (4b design D6, §3.2), placed by [order]. */
class HeaderItem(val id: String, val order: Int, val content: @Composable () -> Unit)
