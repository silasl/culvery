package uk.co.siland.culvery.core.setup.steps

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinInUseException
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.access.ui.ChoosePinPad
import uk.co.siland.culvery.core.household.DuplicateNameException
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.NAME
import uk.co.siland.culvery.core.setup.PIN_SET
import uk.co.siland.culvery.core.setup.PIN_TAKEN
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.PersonRow
import uk.co.siland.culvery.core.setup.SET_YOUR_PIN
import uk.co.siland.culvery.core.setup.SetupDimens
import uk.co.siland.culvery.core.setup.SetupType
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.WHOS_SETTING_UP
import uk.co.siland.culvery.core.setup.showPersonEditor
import uk.co.siland.culvery.core.setup.someoneCalled
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhSwatch
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.PersonPalette

/** The You step's answers until Next makes the Admin; only the PIN's two entries matching fill [pin]. */
@Stable
internal class YouForm {
    var name by mutableStateOf("")
    var color by mutableStateOf(PersonPalette.colors.first())
    var pin by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)

    val ready: Boolean get() = name.isNotBlank() && pin != null
}

/** 4a design §4.2, §3.4: the first Admin — name, colour and PIN. Next makes them and signs them in until Done. */
@Singleton
class YouStep @Inject constructor(
    private val household: HouseholdRepository,
    private val pins: PinManager,
    private val access: AccessControl,
    private val editor: PeopleEditor,
) : SetupStep {
    internal val form = YouForm()

    override val id = "you"
    override val order = 200
    override val done: Flow<Boolean> = household.hasActiveAdmin
    override val canGoOn: Flow<Boolean> = combine(done, snapshotFlow { form.ready }) { made, ready -> made || ready }

    /** Makes the first Admin with their PIN, once: after a kill, or on Back then Next, the Admin already there is kept. */
    override suspend fun onNext(): Boolean {
        if (household.hasActiveAdmin.first()) return true
        val pin = form.pin ?: return false
        return try {
            val admin = pins.addPerson(form.name, form.color, Role.ADMIN, pin)
            access.beginSetupSession(Identified(admin, Role.ADMIN))
            form.pin = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: DuplicateNameException) {
            form.message = someoneCalled(e.name)
            false
        } catch (e: PinInUseException) {
            form.message = PIN_TAKEN
            false
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't add the first Admin (${e::class.simpleName})")
            form.message = COULD_NOT_SAVE
            false
        }
    }

    @Composable
    override fun Content(onNext: () -> Unit) {
        val members by editor.members.collectAsState(initial = emptyList())
        val admin = members.firstOrNull { it.isActiveAdmin }
        val overlay = LocalOverlayHost.current
        // A PIN typed but never confirmed with Next isn't kept once the step is left (Back).
        DisposableEffect(Unit) { onDispose { form.pin = null } }
        StepTitle(WHOS_SETTING_UP)
        if (admin != null) {
            // Ruling 18: once made, the Admin is edited in the sheet like anyone else.
            PersonRow(admin) {
                overlay.showPersonEditor(
                    editor,
                    admin,
                    taken = members.filter { it != admin }.mapTo(HashSet()) { it.person.color },
                    lastAdmin = members.count { it.isActiveAdmin } == 1,
                )
            }
        } else {
            YouFormContent(form, onSetPin = {
                overlay.show {
                    ChoosePinPad(
                        onChosen = {
                            form.pin = it
                            form.message = null
                            overlay.dismiss()
                        },
                        onCancel = overlay::dismiss,
                        drawScrim = false,
                    )
                }
            })
        }
    }

    private companion object {
        const val TAG = "YouStep"
    }
}

@Composable
internal fun YouFormContent(form: YouForm, onSetPin: () -> Unit) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap)) {
        HhTextField(
            value = form.name,
            onValueChange = {
                form.name = it
                form.message = null
            },
            placeholder = NAME,
            tag = "you_name",
            capitalization = KeyboardCapitalization.Words,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
            verticalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
        ) {
            PersonPalette.colors.forEachIndexed { i, colour ->
                HhSwatch(Color(colour), chosen = form.color == colour, taken = false, tag = "you_swatch_$i") { form.color = colour }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SetupDimens.rowDotGap)) {
            HhPillButton(SET_YOUR_PIN, onSetPin, Modifier.testTag("you_set_pin"))
            if (form.pin != null) Text(PIN_SET, style = SetupType.secondary, color = c.mute, modifier = Modifier.testTag("you_pin_set"))
        }
        form.message?.let { Text(it, style = SetupType.message, color = c.danger, modifier = Modifier.testTag("you_message")) }
    }
}
