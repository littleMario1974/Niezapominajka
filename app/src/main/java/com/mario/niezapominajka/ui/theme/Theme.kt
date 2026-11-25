package com.mario.niezapominajka.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val NiezapominajkaColorScheme = lightColorScheme(
    primary = RoseDark,
    onPrimary = TextMain,
    primaryContainer = RoseLight,
    onPrimaryContainer = TextMain,
    inversePrimary = RoseLight,
    secondary = Rose,
    onSecondary = TextMain,
    secondaryContainer = SurfaceCream,
    onSecondaryContainer = TextMain,
    tertiary = RoseMuted,
    onTertiary = TextMain,
    tertiaryContainer = SurfaceVariant,
    onTertiaryContainer = TextMain,
    background = Ecru,
    onBackground = TextMain,
    surface = SurfaceCream,
    onSurface = TextMain,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextMuted,
    surfaceTint = RoseDark,
    inverseSurface = TextMain,
    inverseOnSurface = Ecru,
    error = RoseDark,
    onError = TextMain,
    errorContainer = SurfaceVariant,
    onErrorContainer = TextMain,
    outline = BorderSoft,
    outlineVariant = BorderSoft
)

/**
 * Stały motyw aplikacji. Nie korzysta z kolorów dynamicznych Androida
 * ani z ustawienia jasny/ciemny motyw systemu, dzięki czemu interfejs
 * ma taki sam wygląd na różnych telefonach.
 */
@Composable
fun NiezapominajkaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NiezapominajkaColorScheme,
        typography = Typography,
        content = content
    )
}
