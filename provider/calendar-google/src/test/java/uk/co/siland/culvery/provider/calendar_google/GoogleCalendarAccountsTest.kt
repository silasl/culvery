package uk.co.siland.culvery.provider.calendar_google

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.StoredConnection
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

class GoogleCalendarAccountsTest {
    private fun connection(id: String, providerId: String, account: String) = StoredConnection(
        Connection(id, providerId, "Label", mapOf(CONFIG_ACCOUNT to account)),
        ConnectionHealth.Ok,
        lastSyncMillis = null,
    )

    @Test
    fun listsTheGoogleConnectionsAccounts() = runTest {
        val family = connection("g1", GOOGLE_PROVIDER_ID, "family@example.com")
        val sam = connection("g2", GOOGLE_PROVIDER_ID, "sam@example.com")
        val sample = connection("s1", "calendar.sample", "alex@example.com")
        val stored = MutableStateFlow(listOf(family, sample, sam))
        val accounts = GoogleCalendarAccounts(stored)

        assertThat(accounts.connected.first()).containsExactly("family@example.com", "sam@example.com").inOrder()

        stored.value = listOf(sample, sam)
        assertThat(accounts.connected.first()).containsExactly("sam@example.com")
    }

    @Test
    fun anAccountConnectedTwiceIsListedOnce() = runTest {
        val twice = listOf(
            connection("g1", GOOGLE_PROVIDER_ID, "family@example.com"),
            connection("g2", GOOGLE_PROVIDER_ID, "family@example.com"),
        )
        assertThat(GoogleCalendarAccounts(MutableStateFlow(twice)).connected.first()).containsExactly("family@example.com")
    }
}
