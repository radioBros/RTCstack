package com.rtcstack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * RTCstack design tokens, ported from `packages/ui-react/src/styles.css`.
 * Dark is the default theme (matches the web kit's `:root`); light mirrors `[data-theme="light"]`.
 */
public data class RTCstackColors(
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val border: Color,
    val borderSubtle: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val danger: Color,
    val success: Color,
    val speakingRing: Color,
    val captionBg: Color,
    val captionText: Color,
)

public object RTCstackTokens {
    public val dark: RTCstackColors = RTCstackColors(
        bg = Color(0xFF0F0F0F),
        surface1 = Color(0xFF1A1A1A),
        surface2 = Color(0xFF252525),
        border = Color(0xFF333333),
        borderSubtle = Color(0xFF2A2A2A),
        text = Color(0xFFF5F5F5),
        textMuted = Color(0xFF999999),
        accent = Color(0xFF4F9CF9),
        danger = Color(0xFFE74C3C),
        success = Color(0xFF2ECC71),
        speakingRing = Color(0xFF4F9CF9),
        captionBg = Color(0xB8000000),
        captionText = Color(0xFFFFFFFF),
    )

    public val light: RTCstackColors = dark.copy(
        bg = Color(0xFFF5F5F5),
        surface1 = Color(0xFFFFFFFF),
        surface2 = Color(0xFFEEEEEE),
        border = Color(0xFFDDDDDD),
        borderSubtle = Color(0xFFE5E5E5),
        text = Color(0xFF111111),
        textMuted = Color(0xFF666666),
    )

    public object Radius {
        public val sm: androidx.compose.ui.unit.Dp = 4.dp
        public val md: androidx.compose.ui.unit.Dp = 8.dp
        public val lg: androidx.compose.ui.unit.Dp = 12.dp
    }
}

public val LocalRTCstackColors: androidx.compose.runtime.ProvidableCompositionLocal<RTCstackColors> =
    staticCompositionLocalOf { RTCstackTokens.dark }

/** Convenience accessor: `RTCstackTheme.colors.accent`. */
public object RTCstackTheme {
    public val colors: RTCstackColors
        @Composable get() = LocalRTCstackColors.current
}

@Composable
public fun RTCstackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) RTCstackTokens.dark else RTCstackTokens.light
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.bg,
            surface = colors.surface1,
            error = colors.danger,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.bg,
            surface = colors.surface1,
            error = colors.danger,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    }
    CompositionLocalProvider(LocalRTCstackColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = MaterialTheme.shapes.copy(
                small = RoundedCornerShape(RTCstackTokens.Radius.sm),
                medium = RoundedCornerShape(RTCstackTokens.Radius.md),
                large = RoundedCornerShape(RTCstackTokens.Radius.lg),
            ),
            content = content,
        )
    }
}
