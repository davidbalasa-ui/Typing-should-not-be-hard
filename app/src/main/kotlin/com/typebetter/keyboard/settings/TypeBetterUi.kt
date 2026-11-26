package com.typebetter.keyboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes

/*
 * The building blocks of TypeBetter's own settings screens (docs/decisions/2026-11-26-personal-settings-page.md):
 * a big serif title, small monospace section labels and plain rows on hairlines instead of cards. Colours
 * come from the Material scheme, so dark mode and a user-picked accent keep working.
 */

/** The big serif page heading ("Hi David", "My words"). */
@Composable
internal fun TbDisplayTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        fontFamily = FontFamily.Serif,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** A short grey paragraph under a title. */
@Composable
internal fun TbIntro(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Small monospace capitals above a group of rows. */
@Composable
internal fun TbSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 2.dp)
            .semantics { heading() },
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A plain settings row on a hairline. A chevron shows when it opens something and has no trailing control. */
@Composable
internal fun TbRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 60.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                if (!summary.isNullOrBlank()) {
                    Text(summary, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TbHairline()
    }
}

/**
 * A row with an on/off switch. The switch flips the setting; the rest of the row opens its full settings
 * screen when [onOpen] is given, and otherwise flips the switch too.
 */
@Composable
internal fun TbSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    onOpen: (() -> Unit)? = null,
) {
    TbRow(
        title = title,
        modifier = modifier,
        summary = summary,
        icon = icon,
        onClick = onOpen ?: { onCheckedChange(!checked) },
        // The switch is its own focus stop for TalkBack, so it carries the row's name.
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title },
            )
        },
    )
}

@Composable
internal fun TbHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** The rule tag beside a word: shape (symbol) and text tell them apart, not colour alone. */
@Composable
internal fun TbWordTag(tag: WordTag, modifier: Modifier = Modifier) {
    if (tag == WordTag.NONE) return
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scheme = MaterialTheme.colorScheme
    val (label, colors) = when (tag) {
        WordTag.AUTO_FIX -> stringRes(R.string.typebetter__tag_auto) to
            (if (dark) scheme.secondaryContainer to scheme.onSecondaryContainer else Color(0xFFE1F0E8) to Color(0xFF0F5A43))
        WordTag.SUGGEST_ONLY -> stringRes(R.string.typebetter__tag_suggest) to
            (if (dark) Color(0xFF4A3A12) to Color(0xFFF6E6C4) else Color(0xFFF6E6C4) to Color(0xFF6B4700))
        WordTag.NEVER_CHANGE -> stringRes(R.string.typebetter__tag_never) to
            (if (dark) scheme.surfaceVariant to scheme.onSurface else Color(0xFFE6E9F1) to Color(0xFF1E2A4A))
        WordTag.NONE -> return
    }
    val glyph = when (tag) {
        WordTag.AUTO_FIX -> "↳ "
        WordTag.SUGGEST_ONLY -> "◇ "
        else -> "▣ "
    }
    Text(
        text = glyph + label,
        modifier = modifier
            .semantics { contentDescription = label }
            .background(colors.first, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = colors.second,
    )
}
