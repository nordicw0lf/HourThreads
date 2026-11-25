package app.hourstracker

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import java.util.Locale

internal enum class AppLanguage(private val tag: String, @param:StringRes @get:StringRes val label: Int) {
    Device("", R.string.device_language),
    English("en", R.string.language_english),
    Hebrew("he", R.string.language_hebrew),
    Japanese("ja", R.string.language_japanese);

    fun select() {
        restoreAppLanguage(tag)
    }

    companion object {
        fun current(): AppLanguage {
            val language = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return Device
            return entries.firstOrNull { Locale.forLanguageTag(it.tag).language == language } ?: Device
        }
    }
}

internal fun appLanguageTags(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

internal fun restoreAppLanguage(tags: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
}

@Composable
internal fun LanguagePicker(onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    val current = AppLanguage.current()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (language in AppLanguage.entries) {
                    val selected = language == current
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = selected,
                            onClick = { onSelect(language) },
                            role = Role.RadioButton,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(stringResource(language.label))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
