package com.limelight.ligase

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Android projection of Ligase visual-color-tokens-v1.
 *
 * Keep this file aligned with Ligase Host commit 5d4a20d8. Product UI must use
 * this theme or [LigaseSemanticTheme.colors], rather than introducing a local
 * palette. Stream, video artwork overlays, and TouchKit intentionally remain
 * outside this contract.
 */
@Immutable
internal data class LigaseSemanticColors(
    val brandPrimary: Color,
    val brandSecondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val selected: Color,
    val success: Color,
    val warning: Color,
    val errorDanger: Color,
    val disabled: Color,
    val focus: Color,
)

internal val LigaseLightSemanticColors = LigaseSemanticColors(
    brandPrimary = Color(0xFF6258D9),
    brandSecondary = Color(0xFF08788B),
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2F8),
    textPrimary = Color(0xFF20232C),
    textSecondary = Color(0xFF555D6D),
    border = Color(0xFF8B94A5),
    selected = Color(0xFFE7E5FF),
    success = Color(0xFF13795B),
    warning = Color(0xFF8A4F00),
    errorDanger = Color(0xFFB42318),
    disabled = Color(0xFF555D6D),
    focus = Color(0xFF4F46C7),
)

internal val LigaseDarkSemanticColors = LigaseSemanticColors(
    brandPrimary = Color(0xFFB8B1FF),
    brandSecondary = Color(0xFF6ED6E4),
    background = Color(0xFF15171D),
    surface = Color(0xFF20232C),
    surfaceVariant = Color(0xFF2A2E39),
    textPrimary = Color(0xFFF5F7FB),
    textSecondary = Color(0xFFB8C0CE),
    border = Color(0xFF747D8E),
    selected = Color(0xFF35315C),
    success = Color(0xFF56D19B),
    warning = Color(0xFFF4B860),
    errorDanger = Color(0xFFFF7B72),
    disabled = Color(0xFFB8C0CE),
    focus = Color(0xFFB8B1FF),
)

private val LocalLigaseSemanticColors = staticCompositionLocalOf {
    LigaseLightSemanticColors
}

internal object LigaseSemanticTheme {
    val colors: LigaseSemanticColors
        @Composable get() = LocalLigaseSemanticColors.current
}

internal fun ligaseColorScheme(
    colors: LigaseSemanticColors,
    dark: Boolean,
): ColorScheme {
    // ColorScheme doesn't retain whether a light/dark factory created it. Since
    // every slot is explicit, one factory avoids two mappings drifting apart.
    return lightColorScheme(
        primary = colors.brandPrimary,
        onPrimary = colors.surface,
        primaryContainer = colors.selected,
        onPrimaryContainer = colors.textPrimary,
        inversePrimary = colors.brandPrimary,
        secondary = colors.brandSecondary,
        onSecondary = colors.surface,
        secondaryContainer = colors.surfaceVariant,
        onSecondaryContainer = colors.textPrimary,
        tertiary = colors.brandSecondary,
        onTertiary = colors.surface,
        tertiaryContainer = colors.surfaceVariant,
        onTertiaryContainer = colors.textPrimary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.textSecondary,
        surfaceTint = colors.brandPrimary,
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.surface,
        error = colors.errorDanger,
        onError = colors.surface,
        errorContainer = colors.surfaceVariant,
        onErrorContainer = colors.errorDanger,
        outline = colors.border,
        outlineVariant = colors.border,
        scrim = if (dark) colors.background else colors.textPrimary,
        surfaceBright = colors.surface,
        surfaceDim = colors.background,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surfaceVariant,
        surfaceContainerHigh = colors.surfaceVariant,
        surfaceContainerHighest = colors.surfaceVariant,
        primaryFixed = colors.brandPrimary,
        primaryFixedDim = colors.brandPrimary,
        onPrimaryFixed = colors.surface,
        onPrimaryFixedVariant = colors.surface,
        secondaryFixed = colors.brandSecondary,
        secondaryFixedDim = colors.brandSecondary,
        onSecondaryFixed = colors.surface,
        onSecondaryFixedVariant = colors.surface,
        tertiaryFixed = colors.brandSecondary,
        tertiaryFixedDim = colors.brandSecondary,
        onTertiaryFixed = colors.surface,
        onTertiaryFixedVariant = colors.surface,
    )
}

/**
 * Material 3 1.4.0 has no ColorScheme.shadow slot. Keep the reviewed overlay
 * primitive deterministic for any Ligase component that needs an explicit
 * shadow color, instead of silently using a Material factory default.
 */
internal fun ligaseShadowOverlay(
    colors: LigaseSemanticColors,
    dark: Boolean,
): Color = if (dark) colors.background else colors.textPrimary

internal val LigaseLightColorScheme =
    ligaseColorScheme(LigaseLightSemanticColors, dark = false)

internal val LigaseDarkColorScheme =
    ligaseColorScheme(LigaseDarkSemanticColors, dark = true)

@Composable
internal fun LigaseComposeTheme(
    themeMode: LigaseThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        LigaseThemeMode.SYSTEM -> isSystemInDarkTheme()
        LigaseThemeMode.LIGHT -> false
        LigaseThemeMode.DARK -> true
    }
    val semanticColors = if (dark) LigaseDarkSemanticColors else LigaseLightSemanticColors
    val colorScheme = if (dark) LigaseDarkColorScheme else LigaseLightColorScheme
    CompositionLocalProvider(LocalLigaseSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
