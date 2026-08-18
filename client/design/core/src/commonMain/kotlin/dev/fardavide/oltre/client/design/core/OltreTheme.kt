package dev.fardavide.oltre.client.design.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Translations

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

// **The language is provided here rather than around this**, and it is the same argument the palette
// makes by being here at all: a frame, a preview and a behaviour test all wrap themselves in the
// theme already, so putting the ambient inside it is what makes every one of them draw the words the
// app draws rather than fall back to a default. `App` passes the table the shell resolved; everything
// else gets `English`, which is the only one there is until #87.
@Composable
fun OltreTheme(translations: Translations = English, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTranslations provides translations) {
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
}
