package app.hourstracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun displayLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun LedgerCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    Surface(
        modifier = modifier,
        color = colors.surface,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = if (dark) 1f else .48f)),
        shadowElevation = if (dark) 0.dp else 2.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
internal fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill = if (enabled) {
        Modifier.background(Brush.horizontalGradient(listOf(colors.secondary, colors.tertiary)))
    } else {
        Modifier
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
        shape = CircleShape,
        color = colors.surfaceVariant,
        contentColor = if (enabled) Color.White else colors.onSurfaceVariant,
        shadowElevation = if (enabled && colors.background.luminance() >= .5f) 4.dp else 0.dp,
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).then(fill).padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun SecondaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun LedgerFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text) },
        leadingIcon = leadingIcon,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainer,
            labelColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
        ),
        border = null,
    )
}

@Composable
internal fun LedgerNumber(
    value: String,
    maximum: TextUnit,
    modifier: Modifier = Modifier,
    alignment: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = value,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.displayLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = "tnum",
            textAlign = alignment,
            letterSpacing = (-0.7).sp,
            lineHeight = TextUnit.Unspecified,
            textGeometricTransform = TextGeometricTransform(scaleX = .9f),
        ),
        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = maximum, stepSize = 1.sp),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
internal fun JobMarker(jobId: Long, modifier: Modifier = Modifier, badged: Boolean = true) {
    val color = jobMarkerColors[((jobId % jobMarkerColors.size + jobMarkerColors.size) % jobMarkerColors.size).toInt()]
    Box(
        modifier.size(if (badged) 34.dp else 8.dp).background(color.copy(alpha = if (badged) .16f else 1f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (badged) Box(Modifier.size(10.dp).background(color, CircleShape))
    }
}

private val jobMarkerColors = listOf(
    Color(0xFF2785E7), Color(0xFF00A898), Color(0xFF7758E9), Color(0xFFDA8C23), Color(0xFFE56575),
)

@Composable
internal fun AddIcon(description: String) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
        val middle = size.width / 2
        val edge = size.width * .22f
        val stroke = 2.8.dp.toPx()
        drawLine(color, Offset(edge, middle), Offset(size.width - edge, middle), stroke, StrokeCap.Round)
        drawLine(color, Offset(middle, edge), Offset(middle, size.height - edge), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun durationText(millis: Long): String {
    val minutes = millis / 60_000
    return stringResource(R.string.duration_value, minutes / 60, minutes % 60)
}

@Composable
internal fun BackButton(onClick: () -> Unit) =
    ArrowButton(stringResource(R.string.back), forward = false, onClick)

@Composable
internal fun ArrowButton(description: String, forward: Boolean, onClick: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.size(22.dp)) {
            val from = if (rtl xor forward) size.width * .33f else size.width * .67f
            val to = size.width - from
            val middle = Offset(to, size.height * .5f)
            drawLine(color, Offset(from, size.height * .2f), middle, 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, middle, Offset(from, size.height * .8f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
internal fun NavIcon(tab: HoursTab) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        when (tab) {
            HoursTab.Log -> {
                drawRoundRect(color, Offset(w * .19f, h * .10f), Size(w * .62f, h * .79f),
                    CornerRadius(2.dp.toPx()), style = Stroke(stroke))
                for (y in listOf(.36f, .54f, .72f)) {
                    drawLine(color, Offset(w * .34f, h * y), Offset(w * .66f, h * y), stroke, StrokeCap.Round)
                }
            }
            HoursTab.Home -> {
                val outline = Path().apply {
                    moveTo(w * .09f, h * .48f)
                    lineTo(w * .50f, h * .12f)
                    lineTo(w * .91f, h * .48f)
                    lineTo(w * .80f, h * .48f)
                    lineTo(w * .80f, h * .87f)
                    lineTo(w * .20f, h * .87f)
                    lineTo(w * .20f, h * .48f)
                    close()
                }
                drawPath(outline, color, style = Stroke(stroke))
            }
            HoursTab.Summary -> {
                for ((x, top) in listOf(.23f to .54f, .50f to .36f, .77f to .16f)) {
                    drawLine(
                        color, Offset(w * x, h * top), Offset(w * x, h * .84f),
                        2.6.dp.toPx(), StrokeCap.Round,
                    )
                }
            }
            HoursTab.Info -> {
                drawCircle(color, radius = w * .39f, style = Stroke(stroke))
                drawCircle(color, radius = stroke * .6f, center = Offset(w * .5f, h * .33f))
                drawLine(color, Offset(w * .5f, h * .48f), Offset(w * .5f, h * .69f), stroke, StrokeCap.Round)
            }
        }
    }
}
