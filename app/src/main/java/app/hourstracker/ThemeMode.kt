package app.hourstracker

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

internal enum class ThemeMode(
    val key: String,
    @param:StringRes @get:StringRes val label: Int,
    val nightMode: Int,
) {
    System("system", R.string.theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    Light("light", R.string.theme_light, AppCompatDelegate.MODE_NIGHT_NO),
    Dark("dark", R.string.theme_dark, AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromKey(key: String?): ThemeMode? = entries.firstOrNull { it.key == key }
    }
}
