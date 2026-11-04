package uk.co.siland.culvery.provider.calendar_midsussex

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.FULL_DATE
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.binNames
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.today
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.HhType

internal const val BINS_LABEL = "Mid Sussex bins"
internal const val NO_ADDRESS = "No address found. Check the house name and postcode."
internal const val COUNCIL_UNREADABLE = "Can’t read the council’s site."

private val GAP = 12.dp

private sealed interface Step {
    data object Idle : Step
    data object Working : Step
    data object NoAddress : Step
    /** [house] and [postcode] are what was searched, whatever the fields hold now. */
    data class Pick(val house: String, val postcode: String, val found: CouncilClient.Found) : Step
    data class Ready(val house: String, val postcode: String, val line: String, val next: String) : Step
    /** [ready] has gone to onConnected and is still shown; a second tap adds nothing. */
    data class Done(val ready: Ready) : Step
    data object Failed : Step
}

/** "Next: Monday 9 November — Food": the first collection from [today] on, and the bins in it. */
internal fun nextLine(days: List<BinDay>, today: LocalDate): String {
    val next = days.firstOrNull { !it.date.isBefore(today) } ?: days.first()
    return "Next: ${FULL_DATE.format(next.date)} — " + binNames(next.kinds)
}

private class Connecting(
    private val council: CouncilClient,
    private val scope: CoroutineScope,
    private val today: suspend () -> LocalDate,
) {
    var step by mutableStateOf<Step>(Step.Idle)
        private set

    /** What Try again runs: the last step, or the retry it gave. */
    private var last: suspend () -> Step = { Step.Idle }

    fun search(house: String, postcode: String) = run({
        val found = council.search(house, postcode)
        when (found.addresses.size) {
            0 -> Step.NoAddress
            1 -> found.addresses.single().let { address -> ready(house, postcode, address.line) { council.readAddress(found, address) } }
            else -> Step.Pick(house, postcode, found)
        }
    })

    /** Try again walks from the start: the council may have ended the search's session. */
    fun pick(from: Step.Pick, address: Address) = run(
        { ready(from.house, from.postcode, address.line) { council.readAddress(from.found, address) } },
        retry = { ready(from.house, from.postcode, address.line) { council.read(from.house, from.postcode, address.line) } },
    )

    fun retry() = run(last)

    fun add(existing: Connection?, onConnected: (Connection) -> Unit) {
        val ready = step as? Step.Ready ?: return
        step = Step.Done(ready)
        val config = mapOf(CONFIG_HOUSE to ready.house, CONFIG_POSTCODE to ready.postcode, CONFIG_ADDRESS to ready.line)
        onConnected(Connection(existing?.id ?: UUID.randomUUID().toString(), MIDSUSSEX_PROVIDER_ID, BINS_LABEL, config))
    }

    private suspend fun ready(house: String, postcode: String, line: String, read: suspend () -> List<BinDay>) =
        Step.Ready(house, postcode, line, nextLine(read(), today()))

    /** A tap while a step runs is dropped: the button's disabled look can lag a frame behind. */
    private fun run(block: suspend () -> Step, retry: suspend () -> Step = block) {
        if (step == Step.Working) return
        last = retry
        step = Step.Working
        scope.launch {
            step = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (failed: BinsReadFailed) {
                Log.w(TAG, "Bins connect failed: ${failed.kind.name}")
                Step.Failed
            } catch (e: UnreachableException) {
                Log.w(TAG, "Bins connect failed (${e::class.simpleName})")
                Step.Failed
            }
        }
    }
}

/** The connect form (spec 3.6): house and postcode, the address if the council lists several, then the next collection. */
@Composable
internal fun MidSussexConnectScreen(
    council: CouncilClient,
    clock: WallClock,
    zone: suspend () -> ZoneId,
    existing: Connection?,
    onConnected: (Connection) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val connecting = remember { Connecting(council, scope) { clock.today(zone()) } }
    var house by remember { mutableStateOf(existing?.config?.get(CONFIG_HOUSE).orEmpty()) }
    var postcode by remember { mutableStateOf(existing?.config?.get(CONFIG_POSTCODE).orEmpty()) }
    val step = connecting.step
    val ready = (step as? Step.Done)?.ready ?: step as? Step.Ready
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        if (ready != null) {
            Text(ready.next, style = HhType.secondary, color = Culvery.colors.ink, modifier = Modifier.testTag("bins_next"))
            // The connecting card below has the Cancel.
            HhButton("Add calendar", { connecting.add(existing, onConnected) }, tone = ButtonTone.Primary, on = ButtonSurface.Surf)
        } else {
            val working = step is Step.Working
            HhTextField(
                house, { house = it }, placeholder = "House name or number", label = "House name or number", tag = "bins_house",
                capitalization = KeyboardCapitalization.Words, fill = Culvery.colors.surf2,
            )
            HhTextField(
                postcode, { postcode = it }, placeholder = "Postcode", label = "Postcode", tag = "bins_postcode",
                capitalization = KeyboardCapitalization.Characters, fill = Culvery.colors.surf2,
            )
            HhButton(
                "Find the address",
                { connecting.search(house.trim(), postcode.trim()) },
                tone = ButtonTone.Primary,
                enabled = house.isNotBlank() && postcode.isNotBlank() && !working,
                busy = working,
                on = ButtonSurface.Surf,
            )
            when (step) {
                Step.NoAddress -> Text(NO_ADDRESS, style = HhType.secondary, color = Culvery.colors.danger)
                is Step.Pick -> step.found.addresses.forEach { address ->
                    HhListRow(address.line, onClick = { connecting.pick(step, address) })
                }
                Step.Failed -> {
                    Text(COUNCIL_UNREADABLE, style = HhType.secondary, color = Culvery.colors.danger)
                    HhButton("Try again", connecting::retry, on = ButtonSurface.Surf)
                }
                else -> Unit
            }
        }
    }
}
