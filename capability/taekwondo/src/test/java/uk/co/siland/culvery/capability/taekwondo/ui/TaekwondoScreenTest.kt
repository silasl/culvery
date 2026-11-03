package uk.co.siland.culvery.capability.taekwondo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.taekwondo.FakeAlerts
import uk.co.siland.culvery.capability.taekwondo.FakeSpeaker
import uk.co.siland.culvery.capability.taekwondo.TaekwondoSettings
import uk.co.siland.culvery.core.plugin.LocalAlertHost
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class TaekwondoScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { TaekwondoSettings(settingsDataStore(scope) { File(folder.root, "taekwondo_settings.preferences_pb") }) }
    private val speaker = FakeSpeaker()
    private val overlay = TestOverlay()
    private val alerts = FakeAlerts()
    private var closed = 0
    private var showing by mutableStateOf(true)

    @After
    fun tearDown() = scope.cancel()

    private fun content() = compose.setContent {
        CulveryTheme(dark = true) {
            CompositionLocalProvider(LocalOverlayHost provides overlay, LocalAlertHost provides alerts) {
                if (showing) TaekwondoScreen(settings, speaker) { closed++ }
            }
        }
    }

    /** The screen with the test clock held, so a second is only as long as the test says. */
    private fun show() {
        compose.mainClock.autoAdvance = false
        content()
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun after(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        settle()
    }

    private fun tag(tag: String) = compose.onNodeWithTag(tag)

    private fun open(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun start() {
        tag("pattern_0").performClick()
        settle()
    }

    private fun switchIsOn() = runCatching { tag("taekwondo_detailed").assertIsOn() }.isSuccess

    @Test
    fun theGridShowsChoongMoo() {
        show()

        tag("pattern_0").assertTextEquals("Choong-Moo")
        tag("taekwondo_title").assertTextEquals("Taekwondo")
        tag("taekwondo_detailed").assertIsOff()
        assertThat(open("taekwondo_cancel")).isFalse()
    }

    @Test
    fun aTapCountsDown() {
        show()

        start()

        tag("count").assertTextEquals("10")
        assertThat(tag("count").getUnclippedBoundsInRoot().height.value).isAtLeast(150f)
        tag("taekwondo_title").assertTextEquals("Choong-Moo")
        after(1_000)
        tag("count").assertTextEquals("9")
    }

    @Test
    fun theMoveShowsItsNumberKoreanEnglishAndDescription() {
        show()
        start()

        after(10_000)

        tag("move_number").assertTextEquals("Start")
        tag("move_korean").assertTextEquals("나란히 준비 서기")
        tag("move_romanised").assertTextEquals("Narani junbi sogi")
        tag("move_english").assertTextEquals("Parallel ready stance")
        tag("move_description").assertTextEquals("Feet parallel, shoulder width apart; calm and ready to begin.")
        assertThat(speaker.calls).containsExactly("나란히 준비 서기" to true)
    }

    @Test
    fun cancelStops() {
        show()
        start()
        after(10_500)

        tag("taekwondo_cancel").performClick()
        settle()

        assertThat(open("pattern_0")).isTrue()
        assertThat(speaker.stops).isAtLeast(1)
        after(60_000)
        assertThat(speaker.calls).hasSize(1)
        assertThat(open("move_korean")).isFalse()
    }

    @Test
    fun closeStopsAndCloses() {
        show()
        start()
        after(10_500)

        tag("taekwondo_close").performClick()
        settle()

        assertThat(closed).isEqualTo(1)
        assertThat(speaker.stops).isAtLeast(1)
        after(60_000)
        assertThat(speaker.calls).hasSize(1)
    }

    @Test
    fun detailedIsRemembered() {
        content()
        tag("taekwondo_detailed").assertIsOff().performClick()
        compose.waitUntil(5_000) { runBlocking { settings.detailed.first() } }

        compose.runOnIdle { showing = false }
        compose.runOnIdle { showing = true }

        compose.waitUntil(5_000) { switchIsOn() }
    }

    @Test
    fun detailedReadsTheEnglishNameAndDescriptionToo() {
        runBlocking { settings.setDetailed(true) }
        content()
        compose.waitUntil(5_000) { switchIsOn() }
        compose.mainClock.autoAdvance = false
        start()

        after(12_000)

        assertThat(speaker.calls).containsExactly(
            "나란히 준비 서기" to true,
            "Parallel ready stance" to false,
            "Feet parallel, shoulder width apart; calm and ready to begin." to false,
        ).inOrder()
    }

    @Test
    fun noKoreanVoiceSaysHowToAddOne() {
        speaker.korean.value = false
        show()

        tag("taekwondo_no_korean").assertExists()
        assertThat(speaker.checks).isEqualTo(1)
    }

    @Test
    fun aKoreanVoiceNeedsNoLine() {
        show()

        assertThat(open("taekwondo_no_korean")).isFalse()
    }

    @Test
    fun keptOpenWhileRunning() {
        show()
        assertThat(overlay.keptOpen).isFalse()

        start()
        assertThat(overlay.keptOpen).isTrue()

        tag("taekwondo_cancel").performClick()
        settle()
        assertThat(overlay.keptOpen).isFalse()
    }

    @Test
    fun aRunDoesntStartWhileADoorCardIsUp() {
        show()
        compose.runOnIdle { alerts.show(loud = false) {} }
        settle()

        start()
        after(10_500)

        assertThat(open("pattern_0")).isTrue()
        assertThat(open("count")).isFalse()
        assertThat(speaker.calls).isEmpty()
        assertThat(overlay.keptOpen).isFalse()
    }

    @Test
    fun anyDoorAlertStops() {
        show()
        for (loud in listOf(false, true)) {
            speaker.stops = 0
            start()
            after(10_500)
            assertThat(open("move_korean")).isTrue()

            compose.runOnIdle { alerts.show(loud = loud) {} }
            settle()

            assertThat(open("pattern_0")).isTrue()
            assertThat(open("move_korean")).isFalse()
            assertThat(speaker.stops).isAtLeast(1)
            compose.runOnIdle { alerts.dismiss() }
            settle()
        }
    }
}
