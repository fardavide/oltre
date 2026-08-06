package dev.fardavide.oltre.client.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.fardavide.oltre.client.design.generated.resources.Res
import dev.fardavide.oltre.client.design.generated.resources.jetbrains_mono_bold
import dev.fardavide.oltre.client.design.generated.resources.jetbrains_mono_regular
import dev.fardavide.oltre.client.design.generated.resources.jetbrains_mono_semibold
import org.jetbrains.compose.resources.Font

// Bundled font (JetBrains Mono, OFL — see OFL.txt): text must render identically on every
// platform or screenshot baselines recorded on macOS fail against Linux CI. Family choice is
// provisional design-wise; the determinism is not.
@Composable
fun oltreMono(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
)
