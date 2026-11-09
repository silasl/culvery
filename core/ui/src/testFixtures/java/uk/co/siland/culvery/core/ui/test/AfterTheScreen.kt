package uk.co.siland.culvery.core.ui.test

import org.junit.rules.ExternalResource

/**
 * Runs [close] once the compose rule has disposed the screen, so nothing the screen still reads meets a closed database.
 * It must sit outside the compose rule (a lower `@get:Rule(order)`): `@After` runs while the screen is still up.
 */
class AfterTheScreen(private val close: () -> Unit) : ExternalResource() {
    override fun after() = close()
}
