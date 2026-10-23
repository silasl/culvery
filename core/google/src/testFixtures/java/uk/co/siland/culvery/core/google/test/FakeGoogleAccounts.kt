package uk.co.siland.culvery.core.google.test

import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.google.GoogleAccounts

/** The connected accounts, set by the test. */
class FakeGoogleAccounts(initial: List<String> = emptyList()) : GoogleAccounts {
    override val connected = MutableStateFlow(initial)
}
