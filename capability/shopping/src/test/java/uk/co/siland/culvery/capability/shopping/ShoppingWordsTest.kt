package uk.co.siland.culvery.capability.shopping

import androidx.compose.runtime.Composable
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

/** 10 §4.8, §4.10: the words, with the service named as the provider names itself. */
class ShoppingWordsTest {
    private val now = 1_000_000_000_000L

    @Test
    fun theItemsLineSaysHowFreshTheListIs() {
        assertThat(itemsLine(22, "Google Tasks", now - 30_000, now)).isEqualTo("22 items · from Google Tasks, just now")
        assertThat(itemsLine(1, "Google Tasks", now - 300_000, now)).isEqualTo("1 item · from Google Tasks, 5 minutes ago")
        assertThat(itemsLine(0, "Google Tasks", null, now)).isEqualTo("0 items · not read from Google Tasks yet")
    }

    @Test
    fun eachHealthHasItsLine() {
        val google = GoogleLike
        assertThat(problemLine(ShoppingHealth.OK, google)).isNull()
        assertThat(problemLine(ShoppingHealth.CANT_REACH, google))
            .isEqualTo("Can’t reach Google Tasks. Items you add are kept and sent when it’s back.")
        assertThat(problemLine(ShoppingHealth.NEEDS_PERMISSION, google)).isEqualTo("Allow Google Tasks in Settings › Shopping")
        assertThat(problemLine(ShoppingHealth.LIST_GONE, google))
            .isEqualTo("The Shopping list isn’t in Google Tasks any more. Make a new one in Settings › Shopping.")
        assertThat(problemLine(ShoppingHealth.ACCOUNT_GONE, google))
            .isEqualTo("Shopping’s Google account was disconnected. Connect it again in Settings › Calendars.")
    }

    @Test
    fun theOtherWords() {
        assertThat(QUEUE_FULL).isEqualTo("Shopping can’t hold more until it’s fixed in Settings › Shopping.")
        assertThat(ticked("Milk")).isEqualTo("Ticked Milk")
        assertThat(UNDO).isEqualTo("Undo")
    }

    private object GoogleLike : ShoppingProvider {
        override val descriptor = ProviderDescriptor("shopping.google", "Google Tasks", "", setOf(Feature.READ, Feature.WRITE))
        override val accounts = flowOf(ProviderAccounts.Ready(emptyList()))
        override val accountGoneLine = "Shopping’s Google account was disconnected. Connect it again in Settings › Calendars."

        @Composable
        override fun ConnectScreen(account: String?, existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) = Unit

        override suspend fun remakeList(conn: Connection) = conn

        override suspend fun items(conn: Connection) = emptyList<ShoppingItem>()

        override suspend fun add(conn: Connection, text: String, clientKey: String) = ShoppingItem("g1", text, Instant.EPOCH)

        override suspend fun tick(conn: Connection, id: String) = Unit

        override suspend fun untick(conn: Connection, id: String) = Unit

        override suspend fun history(conn: Connection, since: Instant) = emptyList<ShoppingItem>()
    }
}
