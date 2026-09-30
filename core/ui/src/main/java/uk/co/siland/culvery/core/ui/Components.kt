package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HhCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    color: Color = Culvery.colors.surf,
    padding: PaddingValues = PaddingValues(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(padding),
        content = content,
    )
}

/** A pill button; a disabled one is `surf2` with `mute` text and ignores taps. */
@Composable
fun HhPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Culvery.colors
    Text(
        text = text,
        style = HhType.buttonLabel,
        color = when {
            !enabled -> c.mute
            primary -> c.accentInk
            else -> c.ink
        },
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary && enabled) c.accent else c.surf2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
    )
}
