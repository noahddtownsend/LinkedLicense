package dev.noahtownsend.linkedlicense.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Minimal, dependency-free "X" glyph used for the close button in [LicensesDialog].
 *
 * Drawn directly rather than pulled from an icon library, so this module doesn't force a
 * `material-icons-*` dependency onto every consumer just for two glyphs.
 */
@Composable
internal fun CloseGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val inset = size.minDimension * 0.2f
        val strokeWidth = size.minDimension * 0.1f

        drawLine(
            color = tint,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        drawLine(
            color = tint,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Minimal, dependency-free chevron glyph used for the expand/collapse affordance in
 * [LicensesList]. Points down when [expanded] is `false`, up when `true`.
 */
@Composable
internal fun ChevronGlyph(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val insetX = size.width * 0.22f
        val strokeWidth = size.minDimension * 0.12f
        val topY = if (expanded) size.height * 0.65f else size.height * 0.35f
        val midY = if (expanded) size.height * 0.35f else size.height * 0.65f

        drawLine(
            color = tint,
            start = Offset(insetX, topY),
            end = Offset(size.width / 2f, midY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        drawLine(
            color = tint,
            start = Offset(size.width / 2f, midY),
            end = Offset(size.width - insetX, topY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
