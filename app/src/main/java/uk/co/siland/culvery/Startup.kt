package uk.co.siland.culvery

import uk.co.siland.culvery.core.plugin.Startable

/** One capability failing to start must not stop the others, or the kiosk would come up half-dead. */
internal fun startAll(startables: Iterable<Startable>, onFailure: (Startable, Exception) -> Unit) {
    for (startable in startables) {
        try {
            startable.start()
        } catch (e: Exception) {
            onFailure(startable, e)
        }
    }
}
