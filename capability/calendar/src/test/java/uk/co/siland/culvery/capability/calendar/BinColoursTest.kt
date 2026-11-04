package uk.co.siland.culvery.capability.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BinColoursTest {
    private fun colours(): BinColours {
        val store = object : DataStore<Preferences> {
            private val state = MutableStateFlow(emptyPreferences())
            override val data: Flow<Preferences> = state

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(state.value).also { state.value = it }
        }
        return BinColours(store, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun defaultsAreTheUsersBins() {
        val colours = colours()
        assertThat(BinKind.entries.map { colours.inkFor(it.key)?.id })
            .containsExactly("green", "brown", "blue", "black").inOrder()
    }

    @Test
    fun aChoiceOverridesTheDefault() = runTest {
        val colours = colours()
        colours.set("waste", "grey")
        assertThat(colours.inkFor("waste")?.id).isEqualTo("grey")
        assertThat(colours.inkFor("food")?.id).isEqualTo("green")
    }

    @Test
    fun anUnknownKindHasNoInk() {
        assertThat(colours().inkFor("Glass")).isNull()
    }
}
