package uk.co.siland.culvery.core.plugin

/**
 * Bound `@IntoSet` by a capability that needs background work (e.g. a sync loop).
 * The app calls [start] once from Application.onCreate; it must return quickly and launch its work
 * on the @ApplicationScope scope.
 */
fun interface Startable {
    fun start()
}
