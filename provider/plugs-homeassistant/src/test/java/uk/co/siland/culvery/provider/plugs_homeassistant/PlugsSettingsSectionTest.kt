package uk.co.siland.culvery.provider.plugs_homeassistant

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugSettings
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.plugin.test.RecordingToaster

/** 12 §4.2: the Plugs block Settings › Other integrations asks the Home Assistant provider for. */
@RunWith(AndroidJUnit4::class)
class PlugsSettingsSectionTest {
    @get:Rule val compose = createComposeRule()

    private val store = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = emptyFlow()

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = error("Not used")
    }

    private val access = object : AccessControl {
        override val session: StateFlow<Identified?> = MutableStateFlow(null)
        override val expiresAt: StateFlow<Long?> = MutableStateFlow(null)

        override suspend fun authorise(
            vararg anyOf: String,
            reason: PinReason,
            allow: (Identified, Set<String>) -> Boolean,
            refusal: Refusal,
            label: String?,
        ): Authorised? = null

        override fun beginSetupSession(person: Identified) = Unit

        override fun endSetupSession() = Unit

        override fun touch(holdForEdits: Boolean) = Unit

        override fun checkExpiry() = Unit

        override fun lock() = Unit
    }

    private fun section() = PlugsSettingsSection(
        emptySet(), PlugActions(access, PlugSettings(store), emptySet(), RecordingToaster(), CoroutineScope(Job())), PlugSettings(store),
    )

    @Test
    fun comesAfterTheDevicePickersBlocksAtTen() {
        assertThat(section().order).isEqualTo(10)
    }

    @Test
    fun withNoPlugSystemItRendersNothing() {
        val section = section()
        compose.setContent { Column(Modifier.testTag("host")) { section.Content() } }
        compose.onNodeWithTag("host").onChildren().assertCountEquals(0)
    }
}
