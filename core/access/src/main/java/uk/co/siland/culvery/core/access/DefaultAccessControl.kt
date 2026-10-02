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
    /** Who is signed in, and whether it is the wizard's setup session (4a §3.4): one value, so every end ends both. */
    private class SignedIn(val who: Identified, val setup: Boolean)

    private val signedIn = MutableStateFlow<SignedIn?>(null)
    private val _session = MutableStateFlow<Identified?>(null)
    override val session: StateFlow<Identified?> = _session.asStateFlow()
    private val stateLock = Any()

    /** The one place the session changes. */
    private fun set(next: SignedIn?) = synchronized(stateLock) {
        signedIn.value = next
        _session.value = next?.who
    }

    private var expiry: Job? = null
    // authorise restarts the timer under authoriseLock; touch() restarts it from the UI thread.
    private val expiryLock = Any()
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
            val current = signedIn.value
            if (current != null && current.setup) {
                val grants = grantedFor(current.who.role, anyOf)
                if (grants.isNotEmpty() && allow(current.who, grants)) {
                    restartExpiry(SETUP_IDLE_MS)
                    return@withLock Authorised(current.who.person, current.who.role, grants)
                }
            }
            if (current != null && defs.none { it.freshPin }) {
                val grants = grantedFor(current.who.role, anyOf)
                if (grants.isNotEmpty() && allow(current.who, grants)) {
                    restartExpiry()
                    return@withLock Authorised(current.who.person, current.who.role, grants)
                }
                if (refusal is Refusal.Toast) {
                    toaster.show(refusal.message(current.who.person.name))
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
            // A PIN at the pad starts an ordinary session, whoever it is.
            set(SignedIn(identified, setup = false))
            restartExpiry()
            return Authorised(identified.person, identified.role, granted)
        }
    }

    override fun beginSetupSession(person: Identified) {
        set(SignedIn(person, setup = true))
        restartExpiry(SETUP_IDLE_MS)
    }

    override fun endSetupSession() {
        val current = signedIn.value ?: return
        if (!current.setup) return
        set(SignedIn(current.who, setup = false))
        restartExpiry()
    }

    override fun touch() {
        val current = signedIn.value ?: return
        restartExpiry(if (current.setup) SETUP_IDLE_MS else SESSION_TIMEOUT_MS)
    }

    override fun lock() {
        synchronized(expiryLock) { expiry?.cancel() }
        set(null)
    }

    private fun grantedFor(role: Role, anyOf: Array<out String>): Set<String> =
        anyOf.filter { registry.isGranted(role, it) }.toSet()

    private fun restartExpiry(timeoutMillis: Long = SESSION_TIMEOUT_MS) = synchronized(expiryLock) {
        expiry?.cancel()
        val guarded = signedIn.value
        expiry = scope.launch {
            delay(timeoutMillis)
            synchronized(stateLock) { if (signedIn.value === guarded) set(null) }
        }
    }
}
