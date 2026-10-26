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
        ShoppingHealth.REFUSED -> "$service refused one item. Tick it off on the list to send the rest."
    }
}

const val QUEUE_FULL = "Shopping can’t hold more until it’s fixed in Settings › Shopping."

const val UNDO = "Undo"

/** The toast when an add is past the rate limit; the item stays in the box. */
fun tooFast(text: String): String = "Adding too fast. Wait a moment, then add $text again."

/** The toast after a tick. */
fun ticked(text: String): String = "Ticked $text"

/** Settings › Shopping's copy (10 §4.10). [service] is the provider's name. */
fun useService(service: String): String = "Use $service for the shopping list"

fun stopUsing(service: String): String = "Stop using $service"

fun stayedAllowed(service: String, account: String): String =
    "$service stays allowed for $account until someone removes it in their Google account settings."

fun listGoneOnSettings(service: String): String = "The Shopping list isn’t in $service any more."

/** One change reads in the singular; "will be lost", as the calendar's disconnect says. */
fun stopQuestion(service: String, queued: Int): String =
    "${stopUsing(service)}? The list stays in $service, and suggestions stay here." + when (queued) {
        0 -> ""
        1 -> " 1 unsent change will be lost."
        else -> " $queued unsent changes will be lost."
    }

const val WHICH_ACCOUNT = "Which Google account holds the list?"
const val MAKE_A_NEW_LIST = "Make a new list"
const val CLEAR_SUGGESTIONS = "Clear suggestions"
const val CLEAR_QUESTION = "Clear suggestions? Favourites start again from what’s added next."

fun allow(service: String): String = "Allow $service"
