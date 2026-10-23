package uk.co.siland.culvery.capability.shopping

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Home's + asks the Shopping tab to open with the add box focused and the keyboard up; the tab takes the ask once (10 §4.9). */
@Singleton
class ShoppingFocus @Inject constructor() {
    private val asked = AtomicBoolean(false)

    fun askForAdd() {
        asked.set(true)
    }

    /** True once per [askForAdd]. */
    internal fun takeAddAsk(): Boolean = asked.getAndSet(false)
}
