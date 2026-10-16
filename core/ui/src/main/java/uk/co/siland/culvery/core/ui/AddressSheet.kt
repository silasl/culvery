package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.launch

/** What an address sheet says: its [title] and [hint], the field's [label] and [example], its [button], and [checking] while it checks. */
class AddressSheetWords(
    val title: String,
    val hint: String,
    val label: String,
    val example: String,
    val button: String,
    val checking: String,
)

/**
 * Typing a device's address on the home network: [onConnect] answers null when the device answered, else the line to
 * show. The sheet, its field and its line are tagged [tag] with `_sheet`, `_field` and `_line`; its button [buttonTag].
 */
@Composable
fun HhAddressSheet(
    words: AddressSheetWords,
    tag: String,
    buttonTag: String,
    padding: PaddingValues,
    onConnect: suspend (String) -> String?,
    onEdited: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val c = Culvery.colors
    var typed by remember { mutableStateOf("") }
    var line by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun connect() {
        if (checking || typed.isBlank()) return
        scope.launch {
            checking = true
            line = onConnect(typed)
            checking = false
        }
    }
    HhSheet(padding = padding, modifier = Modifier.testTag("${tag}_sheet").semantics { paneTitle = words.title }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(words.title, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(words.hint, style = HhType.body, color = c.mute)
            }
            HhCloseButton(onClose)
        }
        HhTextField(
            typed,
            {
                typed = it
                line = null
                onEdited(it.isNotEmpty())
            },
            placeholder = words.example, label = words.label, tag = "${tag}_field",
            capitalization = KeyboardCapitalization.None, error = line, onDone = ::connect,
        )
        HhMessageLine(line, HhType.body, c.danger, "${tag}_line", fieldError = line)
        HhButton(
            if (checking) words.checking else words.button, ::connect, Modifier.testTag(buttonTag),
            tone = ButtonTone.Primary, enabled = !checking && typed.isNotBlank(), on = ButtonSurface.Bg,
        )
    }
}
