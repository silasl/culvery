package uk.co.siland.culvery.capability.taekwondo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.soon
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class TaekwondoCapabilityTest {
    @get:Rule val folder = TemporaryFolder()

    private fun capability(scope: CoroutineScope) = TaekwondoCapability(
        TaekwondoSettings(settingsDataStore(scope) { File(folder.root, "taekwondo_settings.preferences_pb") }),
        Provider { FakeSpeaker() },
    )

    /** Fails if it gains a tab or a card, or its id, label, icon or order differs. */
    @Test
    fun itIsTaekwondoWithNoTabAndNoCards() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val capability = capability(scope)

        assertThat(listOf(capability.id, capability.label, capability.icon, capability.order))
            .containsExactly("taekwondo", "Taekwondo", Icons.SPORTS_MARTIAL_ARTS, 95).inOrder()
        assertThat(capability.hasTab.first()).isFalse()
        assertThat(capability.cards().first()).isEmpty()
        scope.cancel()
    }

    /** Fails if the button isn't half width at order 90, after the modes at 10–13 and Hoover at 20. */
    @Test
    fun aHalfButtonAfterTheModes() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val buttons = capability(scope).trayButtons().first()

        assertThat(buttons.map { Triple(it.id, it.order, it.width) }).containsExactly(Triple("taekwondo", 90, TrayWidth.HALF))
        scope.cancel()
    }

    /** Fails if Detailed mode starts on, or a choice is not read back. */
    @Test
    fun detailedIsOffUntilSwitchedOn() = runTest {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = TaekwondoSettings(settingsDataStore(first) { File(folder.root, "taekwondo_settings.preferences_pb") })
        assertThat(soon { settings.detailed.first() }).isFalse()
        soon { settings.setDetailed(true) }
        assertThat(soon { settings.detailed.first() }).isTrue()
        first.cancel()
    }
}
