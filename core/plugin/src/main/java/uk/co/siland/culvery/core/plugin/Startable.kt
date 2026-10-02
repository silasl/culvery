package uk.co.siland.culvery.core.plugin

/**
 * Bound `@IntoSet` by a capability that needs background work (e.g. a sync loop). The app calls [start] once at
 * start-up, off the main thread (4c design §4.1); it must return quickly and launch its work on the @ApplicationScope
 * scope.
 */
fun interface Startable {
    fun start()
}
