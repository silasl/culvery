package uk.co.siland.culvery.core.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.ShellNavigator

/** The tablet's canvas, for every screenshot in this module. */
internal val CANVAS_W = 1280.dp
internal val CANVAS_H = 800.dp

/**
 * Puts Robolectric in touch mode, as the tablet is, so focus and the keyboard behave as they do there (the calendar's
 * rule, copied: test sources aren't shared between modules). Use it at `@get:Rule(order = 0)`, before the compose rule.
 */
class TouchModeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
            base.evaluate()
        }
    }
}

/**
 * Nothing logged under [tag], with its whole chain of causes, holds any of [secrets]; at least [minLines] were logged,
 * so a check that saw no log can't pass by default.
 */
internal fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1) {
    val logs = ShadowLog.getLogs().filter { it.tag == tag }
    assertWithMessage("lines logged under $tag").that(logs.size).isAtLeast(minLines)
    logs.forEach { log ->
        val text = "${log.msg} ${generateSequence(log.throwable) { it.cause }.joinToString(" ")}"
        secrets.forEach { assertWithMessage(text).that(text).doesNotContain(it) }
    }
}

/** Waits for content read from Room or DataStore before a test's first check or tap. */
internal fun ComposeContentTestRule.awaitText(text: String) =
    waitUntil(5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

internal fun ComposeContentTestRule.awaitTag(tag: String) =
    waitUntil(5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

/** A Settings page that draws [content]: the Settings tests' and screenshots' pages. */
class StillPage(override val id: String, override val title: String, override val order: Int, val content: @Composable () -> Unit) : SettingsPage {
    @Composable
    override fun Content() = content()
}

class RecordingNavigator : ShellNavigator {
    var kioskExits = 0

    override fun openTab(id: String) = Unit

    override fun openSettings() = Unit

    override fun exitKiosk() {
        kioskExits++
    }
}
