package com.noryan.nebulos.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noryan.nebulos.ui.theme.HighlightColor

/**
 * One dense, focusable text line (RomRunner's list style): label left, optional value right. While focused the whole
 * line is highlighted (a brand-orange bar with dark text). [onAlt] fires on long-press and on the controller's X / Y buttons (opens the
 * inline fold-down options — no popups anywhere).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusLine(
    text: String,
    modifier: Modifier = Modifier,
    right: String? = null,
    indent: Dp = 0.dp,
    marked: Boolean = false,
    dim: Boolean = false,
    center: Boolean = false,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    onAlt: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val nested = LocalListIndent.current
    val style = if (nested > 0.dp) MaterialTheme.typography.titleSmall else style   // options under an open row read smaller
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val base = if (dim) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
    val sub = if (dim) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
    val onBar = Color.Black
    val c1: Color = if (focused) onBar else if (marked) MaterialTheme.colorScheme.secondary else base
    val c2: Color = if (focused) onBar.copy(alpha = 0.7f) else sub
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) HighlightColor else Color.Transparent)
            .onKeyEvent { e ->
                if (onAlt != null && e.type == KeyEventType.KeyUp &&
                    (e.key == Key.ButtonX || e.key == Key.ButtonY || e.key == Key.Menu)) { onAlt(); true } else false
            }
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onAlt)
            .padding(start = 16.dp + indent + nested, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start
    ) {
        Text(text, style = style, color = c1, maxLines = 2, overflow = TextOverflow.Ellipsis,   // long titles wrap to a second line instead of being cut off
            textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = if (center) Modifier else Modifier.weight(1f))
        if (right != null) {
            Spacer(Modifier.width(12.dp))
            // The value: right-aligned in the right ~60% of the line, wrapping to a few lines for a full folder path.
            Text(right, Modifier.fillMaxWidth(0.62f), style = style, color = c2, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
    }
}
