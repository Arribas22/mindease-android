package com.bravosix.mindease

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Paleta calmante: verdes suaves + azul cielo — evita rojos/naranjas agresivos
private val MindGreen        = Color(0xFF2ECC71)  // verde esmeralda
private val MindGreenDark    = Color(0xFF58D68D)
private val MindTeal         = Color(0xFF1ABC9C)
private val MindLavender     = Color(0xFF8E44AD)
private val SurfaceLight     = Color(0xFFF4FBF6)
private val SurfaceContLight = Color(0xFFFFFFFF)
private val SurfaceDark      = Color(0xFF0E1512)
private val SurfaceContDark  = Color(0xFF172019)

private val LightScheme = lightColorScheme(
    primary             = MindGreen,
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFFD4EFDF),
    onPrimaryContainer  = Color(0xFF0B3D26),
    secondary           = MindTeal,
    onSecondary         = Color.White,
    tertiary            = MindLavender,
    onTertiary          = Color.White,
    background          = SurfaceLight,
    onBackground        = Color(0xFF0D1F14),
    surface             = SurfaceContLight,
    onSurface           = Color(0xFF0D1F14),
    surfaceVariant      = Color(0xFFDCEDE2),
    onSurfaceVariant    = Color(0xFF3D5A47),
    outline             = Color(0xFF8AB596),
    error               = Color(0xFFB00020),
    onError             = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary             = MindGreenDark,
    onPrimary           = Color(0xFF003920),
    primaryContainer    = Color(0xFF005230),
    onPrimaryContainer  = Color(0xFFB7F2CA),
    secondary           = Color(0xFF4DB6AC),
    onSecondary         = Color(0xFF00201D),
    tertiary            = Color(0xFFD7AAFF),
    onTertiary          = Color(0xFF38006B),
    background          = SurfaceDark,
    onBackground        = Color(0xFFD8EBD8),
    surface             = SurfaceContDark,
    onSurface           = Color(0xFFD8EBD8),
    surfaceVariant      = Color(0xFF1D3025),
    onSurfaceVariant    = Color(0xFF9EC9A8),
    outline             = Color(0xFF4A6B52),
    error               = Color(0xFFCF6679),
    onError             = Color(0xFF690020),
)

private val AppTypography = Typography(
    displaySmall   = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium,   letterSpacing = (-0.2).sp),
    headlineSmall  = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge     = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
)

@Composable
fun MindEaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else      -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
