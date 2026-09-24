package uk.co.siland.culvery.core.access

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
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

@Singleton
class DefaultAccessControl @Inject constructor(
    private val registry: PermissionRegistry,
    private val pins: PinManager,
    private val lockout: LockoutStore,
    private val prompt: PinPromptController,
    private val clock: WallClock,
    private val toaster: Toaster,
    @ApplicationScope private val scope: CoroutineScope,
) : AccessControl {
    private val _session = MutableStateFlow<Identified?>(null)
    override val session: StateFlow<Identified?> = _session.asStateFlow()

    private var expiry: Job? = null
    // Serialises callers so repeated taps can't stack PIN pads, and a queued caller sees the session the first one started.
    private val authoriseLock = Mutex()

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? {
        require(anyOf.isNotEmpty()) { "authorise needs at least one permission" }
        val defs = anyOf.map(registry::require)
        return authoriseLock.withLock {
            val current = _session.value
            if (current != null && defs.none { it.freshPin }) {
                val grants = grantedFor(current.role, anyOf)
                if (grants.isNotEmpty() && allow(current, grants)) {
                    restartExpiry()
                    return@withLock Authorised(current.person, current.role, grants)
                }
                if (refusal is Refusal.Toast) {
                    toaster.show(refusal.message(current.person.name))
                    // Signed out, so the next tap brings up the PIN pad: an adult can take over from a child.
                    lock()
                    return@withLock null
                }
            }
            try {
                promptUntilResolved(defs.first().label, reason, anyOf, allow, refusal)
            } finally {
                prompt.dismiss()
            }
        }
    }

    private suspend fun promptUntilResolved(
        label: String,
        reason: PinReason,
        anyOf: Array<out String>,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? {
        var error: PinError? = null
        while (true) {
            val pin = prompt.ask(label, reason, error, lockout.lockedUntil(clock.nowMillis())) ?: return null
            if (lockout.lockedUntil(clock.nowMillis()) != null) continue

            val identified = pins.identify(pin)
            if (identified == null) {
                lockout.recordFailure(clock.nowMillis())
                error = PinError.WrongPin
                continue
            }
            val granted = grantedFor(identified.role, anyOf)
            if (granted.isEmpty() || !allow(identified, granted)) {
                // A real but refused PIN neither resets nor counts, so a child's own PIN can't clear the counter.
                if (refusal is Refusal.Toast) {
                    toaster.show(refusal.message(identified.person.name))
                    return null
                }
                error = PinError.NotAllowed(identified.person.name)
                continue
            }
            lockout.reset()
            _session.value = identified
            restartExpiry()
            return Authorised(identified.person, identified.role, granted)
        }
    }

    override fun lock() {
        expiry?.cancel()
        _session.value = null
    }

    private fun grantedFor(role: Role, anyOf: Array<out String>): Set<String> =
        anyOf.filter { registry.isGranted(role, it) }.toSet()

    private fun restartExpiry() {
        expiry?.cancel()
        val guarded = _session.value
        expiry = scope.launch {
            delay(SESSION_TIMEOUT_MS)
            _session.compareAndSet(guarded, null)
        }
    }
}
