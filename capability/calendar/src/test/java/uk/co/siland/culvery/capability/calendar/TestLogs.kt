package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertWithMessage
import org.robolectric.shadows.ShadowLog

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
