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
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.UserActivity
import uk.co.siland.culvery.core.plugin.WallClock

/** The access rules. [userActivity] gives each wait Android's "Time to take action" when TalkBack is on (4d-2 §6.1). */
@Singleton
class DefaultAccessControl @Inject constructor(
    private val registry: PermissionRegistry,
    private val pins: PinManager,
    private val lockout: LockoutStore,
    private val prompt: PinPromptController,
    private val clock: WallClock,
    private val toaster: Toaster,
    @ApplicationScope private val scope: CoroutineScope,
    private val userActivity: UserActivity,
) : AccessControl {
    /** Who is signed in, and whether it is the wizard's setup session (4a §3.4): one value, so every end ends both. */
    private class SignedIn(val who: Identified, val setup: Boolean)

    private val signedIn = MutableStateFlow<SignedIn?>(null)
    private val _session = MutableStateFlow<Identified?>(null)
    override val session: StateFlow<Identified?> = _session.asStateFlow()
    private val stateLock = Any()
    private val _expiresAt = MutableStateFlow<Long?>(null)
    override val expiresAt: StateFlow<Long?> = _expiresAt.asStateFlow()

    // The last touch's holdForEdits, so an authorise on the session keeps it. Cleared by a touch without it, or when
    // whoever is signed in changes (sign-out included). Written only under stateLock.
    @Volatile private var holding = false

    /** The one place the session changes. Every caller but sign-out restarts the expiry next, which sets [expiresAt] again. */
    private fun set(next: SignedIn?) = synchronized(stateLock) {
        if (next?.who != signedIn.value?.who) holding = false
        signedIn.value = next
        _session.value = next?.who
        _expiresAt.value = null
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
            endIfPastByWallClock()
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
                    toaster.show(refusal.message(current.who.person.name), urgent = true)
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
                // Counted as a wrong PIN and never resets the count, so a sibling's PIN can't be found for free or clear it.
                lockout.recordFailure(clock.nowMillis())
                if (refusal is Refusal.Toast) {
                    toaster.show(refusal.message(identified.person.name), urgent = true)
                    return null
                }
                error = PinError.NotAllowed(refusedHere(openToAdults = anyOf.any { registry.isGranted(Role.ADULT, it) }))
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
        if (endIfPastByWallClock()) return
        // Read and write under one lock: an expiry or lock() in between must not be undone by a stale write.
        synchronized(stateLock) {
            val current = signedIn.value?.takeIf { it.setup } ?: return
            set(SignedIn(current.who, setup = false))
        }
        restartExpiry()
    }

    override fun touch(holdForEdits: Boolean) {
        if (endIfPastByWallClock()) return
        // Read and written together, so an expiry in between can't leave the hold behind for the next person.
        val setup = synchronized(stateLock) { signedIn.value?.setup?.also { holding = holdForEdits } } ?: return
        restartExpiry(if (setup) SETUP_IDLE_MS else SESSION_TIMEOUT_MS)
    }

    override fun checkExpiry() {
        endIfPastByWallClock()
    }

    /** True, with the session ended, once the wall clock has passed [expiresAt]; the expiry's delay stops while the tablet sleeps. */
    private fun endIfPastByWallClock(): Boolean {
        val ends = _expiresAt.value ?: return false
        if (clock.nowMillis() < ends) return false
        lock()
        return true
    }

    override fun lock() {
        synchronized(expiryLock) { expiry?.cancel() }
        set(null)
    }

    private fun grantedFor(role: Role, anyOf: Array<out String>): Set<String> =
        anyOf.filter { registry.isGranted(role, it) }.toSet()

    /** [baseMillis], or the edit hold while [holding]; stretched by TalkBack's time; [expiresAt] set to match. */
    private fun restartExpiry(baseMillis: Long = SESSION_TIMEOUT_MS) = synchronized(expiryLock) {
        expiry?.cancel()
        val guarded = signedIn.value
        val millis = userActivity.timeout(if (holding) maxOf(baseMillis, EDIT_HOLD_MS) else baseMillis)
        _expiresAt.value = guarded?.let { clock.nowMillis() + millis }
        expiry = scope.launch {
            delay(millis)
            synchronized(stateLock) { if (signedIn.value === guarded) set(null) }
        }
    }
}
