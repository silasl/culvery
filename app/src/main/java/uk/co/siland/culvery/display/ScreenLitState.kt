package uk.co.siland.culvery.display

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.ScreenLit
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.shell.ShellAlerts

/** Holds the signal with no dependencies, so pollers can inject it without a cycle through [ShellAlerts] or [DisplayState]. */
@Singleton
class ScreenLitState @Inject constructor() : ScreenLit {
    private val _lit = MutableStateFlow(false)
    override val lit: StateFlow<Boolean> = _lit.asStateFlow()

    internal fun set(lit: Boolean) {
        _lit.value = lit
    }
}

/** Feeds [ScreenLitState] from the resumed signal and the display's screen. */
@Singleton
class ScreenLitFeed internal constructor(
    private val inFront: StateFlow<Boolean>,
    private val screen: StateFlow<Screen>,
    private val into: ScreenLitState,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(
        alerts: ShellAlerts,
        display: DisplayState,
        into: ScreenLitState,
        @ApplicationScope scope: CoroutineScope,
    ) : this(alerts.inFront, display.screen, into, scope)

    override fun start() {
        scope.launch {
            combine(inFront, screen) { f, s -> f && s == Screen.AWAKE }.collect(into::set)
        }
    }
}
