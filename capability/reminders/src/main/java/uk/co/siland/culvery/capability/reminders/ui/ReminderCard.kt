package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.capability.reminders.CardUi
import uk.co.siland.culvery.capability.reminders.StripeUi
import uk.co.siland.culvery.core.ui.BinInks
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhStripedBar
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.Stripe
import uk.co.siland.culvery.core.ui.quietFill
import uk.co.siland.culvery.core.ui.shownNotSpoken

internal const val DONE = "Done"
internal const val LATER = "Later"
internal const val REMINDER = "Reminder"

/**
 * The reminder card (22 §4.3), in the door card's shape: the alarm mark, the words with their stripes, who and when
 * under them, `1 of 2` while several wait, then Later and Done. TalkBack reads the words, who and the count as one description.
 */
@Composable
fun ReminderCard(card: CardUi, onDone: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(RemindersDimens.cardRadius)
    Column(
        verticalArrangement = Arrangement.spacedBy(RemindersDimens.cardGap),
        modifier = modifier
            .testTag("reminder_card")
            .widthIn(max = RemindersDimens.cardWidth)
            .fillMaxWidth()
            .shadow(RemindersDimens.cardShadow, shape)
            .clip(shape)
            .background(c.surf)
            .border(RemindersDimens.cardEdge, c.surf3, shape)
            .padding(RemindersDimens.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RemindersDimens.cardGap)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RemindersDimens.cardGap),
                modifier = Modifier.weight(1f).testTag("reminder_card_words").semantics(mergeDescendants = true) {
                    contentDescription = spoken(card)
                    liveRegion = LiveRegionMode.Assertive
                },
            ) {
                Box(Modifier.size(RemindersDimens.markCircle).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    HhIcon(Icons.ALARM, size = RemindersDimens.markIcon, filled = true, tint = c.accent)
                }
                Row(Modifier.weight(1f).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(RemindersDimens.barGap)) {
                    if (card.stripes.isNotEmpty()) HhStripedBar(card.stripes.map { it.toStripe() }, RemindersDimens.barWidth)
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.words,
                            style = HhType.sectionTitle.copy(fontSize = RemindersDimens.titleSize),
                            color = c.ink,
                            modifier = Modifier.testTag("reminder_words").shownNotSpoken(),
                        )
                        val line = listOf(card.who, card.dueAt).filter { it.isNotBlank() }.joinToString(" · ")
                        if (line.isNotEmpty()) {
                            Text(line, style = HhType.body.copy(fontSize = RemindersDimens.lineSize), color = c.mute, modifier = Modifier.testTag("reminder_line").shownNotSpoken())
                        }
                        card.detail?.let {
                            Text(it, style = HhType.body.copy(fontSize = RemindersDimens.lineSize), color = c.mute, modifier = Modifier.testTag("reminder_detail").shownNotSpoken())
                        }
                    }
                }
            }
            card.of?.let { CountChip(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RemindersDimens.cardGap)) {
            HhButton(LATER, onLater, Modifier.weight(1f).testTag("reminder_later"), size = ButtonSize.Large, on = ButtonSurface.Surf)
            HhButton(DONE, onDone, Modifier.weight(2f).testTag("reminder_done"), tone = ButtonTone.Primary, size = ButtonSize.Large, on = ButtonSurface.Surf)
        }
    }
}

/** The words, who, what is under them and the count, in the order they are read. */
private fun spoken(card: CardUi) =
    listOfNotNull(card.words, card.who.takeIf { it.isNotBlank() }, card.detail, card.of?.let { "1 of $it" }).joinToString(", ")

@Composable
private fun CountChip(of: Int) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(RemindersDimens.chipHeight)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Surf, c))
            .padding(horizontal = RemindersDimens.chipPaddingH)
            .shownNotSpoken(),
    ) {
        Text("1 of $of", style = HhType.label.copy(fontSize = RemindersDimens.chipTextSize), color = c.ink, maxLines = 1)
    }
}

@Composable
private fun StripeUi.toStripe(): Stripe {
    val c = Culvery.colors
    if (colour != null) return Stripe(Color(colour))
    val ink = BinInks.byId(inkId)
    return Stripe(BinInks.colour(ink, c), BinInks.edge(ink, c))
}
