package uk.co.siland.culvery.capability.shopping

import uk.co.siland.culvery.core.ui.agoWords
import uk.co.siland.culvery.core.ui.plural

/** The tab's line under the title: how many items, and how fresh the read is (10 §4.8). */
fun itemsLine(open: Int, service: String, lastReadMillis: Long?, nowMillis: Long): String {
    val count = plural(open.toLong(), "item")
    return if (lastReadMillis == null) {
        "$count · not read from $service yet"
    } else {
        "$count · from $service, ${agoWords(lastReadMillis, nowMillis)}"
    }
}

/** What the tab, the card and Settings say when the list is not healthy; null when it is (10 §4.10). */
fun problemLine(health: ShoppingHealth, provider: ShoppingProvider): String? {
    val service = provider.descriptor.displayName
    return when (health) {
        ShoppingHealth.OK -> null
        ShoppingHealth.CANT_REACH -> "Can’t reach $service. Items you add are kept and sent when it’s back."
        ShoppingHealth.NEEDS_PERMISSION -> "Allow $service in Settings › Shopping"
        ShoppingHealth.LIST_GONE -> "The Shopping list isn’t in $service any more. Make a new one in Settings › Shopping."
        ShoppingHealth.ACCOUNT_GONE -> provider.accountGoneLine
    }
}

const val QUEUE_FULL = "Shopping can’t hold more until it’s fixed in Settings › Shopping."

const val UNDO = "Undo"

/** The toast after a tick. */
fun ticked(text: String): String = "Ticked $text"
