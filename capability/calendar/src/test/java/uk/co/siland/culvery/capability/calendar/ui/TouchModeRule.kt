package uk.co.siland.culvery.capability.calendar.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Puts Robolectric in touch mode, as the tablet is, so focus and the keyboard behave as they do there. It must run
 * before the compose rule launches its activity (`@get:Rule(order = 0)`): set from `@Before`, the window is already
 * out of touch mode and `clearFocus()` hands focus straight back.
 */
class TouchModeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
            base.evaluate()
        }
    }
}
