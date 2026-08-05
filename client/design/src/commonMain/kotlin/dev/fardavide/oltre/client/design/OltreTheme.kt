package dev.fardavide.oltre.client.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette lifted from docs/ui-mockup.html — the UI design brief.
object OltreColors {
    val background = Color(0xFF05070D)
    val surface = Color(0xFF0A0E18)
    val text = Color(0xFFE9EDF5)
    val textSecondary = Color(0xFF8B94A8)
    val textTertiary = Color(0xFF5C6478)
    val accent = Color(0xFF4C8DFF)
    val warn = Color(0xFFFFB454)
    val danger = Color(0xFFFF6B6B)
    val ok = Color(0xFF4ADE80)
    val metal = Color(0xFFAEB9C9)
    val crystal = Color(0xFF5FD0E8)
    val deuterium = Color(0xFFA98BFA)
}

@Composable
fun OltreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OltreColors.accent,
            background = OltreColors.background,
            surface = OltreColors.surface,
            error = OltreColors.danger,
            onPrimary = OltreColors.text,
            onBackground = OltreColors.text,
            onSurface = OltreColors.text,
            onSurfaceVariant = OltreColors.textSecondary,
        ),
        content = content,
    )
}
