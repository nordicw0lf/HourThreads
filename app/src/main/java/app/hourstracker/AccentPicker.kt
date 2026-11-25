package app.hourstracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun AccentPicker(
    dark: Boolean,
    selected: AccentColor,
    onChoose: (AccentColor) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (dark) R.string.accent_dark else R.string.accent_light)) },
        text = {
            FlowRow(
                Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (accent in AccentColor.entries) {
                    val isSelected = accent == selected
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) colors.primaryContainer else colors.surfaceContainer,
                        contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                        border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outlineVariant),
                    ) {
                        Row(
                            Modifier.selectable(isSelected, role = Role.RadioButton, onClick = { onChoose(accent) })
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AccentSwatch(accent.swatch(dark))
                            Text(stringResource(accent.label), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun AccentSwatch(color: Color) {
    Box(
        Modifier.size(22.dp)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}
