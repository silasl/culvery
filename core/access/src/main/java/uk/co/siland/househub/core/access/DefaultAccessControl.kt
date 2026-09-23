package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.househub.core.household.Role
import uk.co.siland.househub.core.plugin.ApplicationScope
import uk.co.siland.househub.core.plugin.WallClock

@Singleton
class DefaultAccessControl @Inject constructor(
    private val registry: PermissionRegistry,
    private val pins: PinManager,
    private val lockout: LockoutStore,
    private val prompt: PinPromptController,
    private val clock: WallClock,
    @ApplicationScope private val scope: CoroutineScope,
) : AccessControl {
    private val _session = MutableStateFlow<Identified?>(null)
    override val session: StateFlow<Identified?> = _session.asStateFlow()

    private var expiry: Job? = null
    // Serialises callers so repeated taps can't stack PIN pads, and a queued caller sees the session the first one started.
    private val authoriseLock = Mutex()

    override suspend fun authorise(vararg anyOf: String): Authorised? {
        require(anyOf.isNotEmpty()) { "authorise needs at least one permission" }
        val defs = anyOf.map(registry::require)
        return authoriseLock.withLock {
            val current = _session.value
            val sessionGrants = current?.let { grantedFor(it.role, anyOf) }.orEmpty()
            if (current != null && sessionGrants.isNotEmpty() && defs.none { it.freshPin }) {
                touch()
                return@withLock Authorised(current.person, current.role, sessionGrants)
            }
            try {
                promptUntilResolved(defs.first().label, anyOf)
            } finally {
                prompt.dismiss()
            }
        }
    }

    private suspend fun promptUntilResolved(label: String, anyOf: Array<out String>): Authorised? {
        var error: PinError? = null
        while (true) {
            val pin = prompt.ask(label, error, lockout.lockedUntil(clock.nowMillis())) ?: return null
            if (lockout.lockedUntil(clock.nowMillis()) != null) continue

            val identified = pins.identify(pin)
            if (identified == null) {
                lockout.recordFailure(clock.nowMillis())
                error = PinError.WrongPin
                continue
            }
            val granted = grantedFor(identified.role, anyOf)
            if (granted.isEmpty()) {
                // A real but unauthorised PIN neither resets nor counts, so a child's own PIN can't clear the counter.
                error = PinError.NotAllowed(identified.person.name)
                continue
            }
            lockout.reset()
            _session.value = identified
            restartExpiry()
            return Authorised(identified.person, identified.role, granted)
        }
    }

    override fun touch() {
        if (_session.value != null) restartExpiry()
    }

    override fun lock() {
        expiry?.cancel()
        _session.value = null
    }

    private fun grantedFor(role: Role, anyOf: Array<out String>): Set<String> =
        anyOf.filter { registry.isGranted(role, it) }.toSet()

    private fun restartExpiry() {
        expiry?.cancel()
        expiry = scope.launch {
            delay(SESSION_TIMEOUT_MS)
            _session.value = null
        }
    }
}
