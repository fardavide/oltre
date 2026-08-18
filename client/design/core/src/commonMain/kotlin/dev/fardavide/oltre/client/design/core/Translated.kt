package dev.fardavide.oltre.client.design.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Translations

// **The seam between what the game says and how it is drawn**, and the only place in the UI half
// where a `TextRes` becomes a `String`. It is a `CompositionLocal` rather than a parameter for the
// reason the palette is: threading the language through every composable signature would put it in
// several hundred of them to be read in about forty.
//
// It sits with the tokens rather than with the components because it is the same kind of thing — an
// ambient the whole tree reads and nobody passes. `:client:design:text` itself stays Compose-free;
// this file is the one line of glue.
//
// **`static`, because the language does not change while the app is running.** A `staticCompositionLocalOf`
// recomposes the entire subtree when its value changes, which is exactly right for a value that
// changes once, at the composition root, or never.
//
// The default is `English` rather than an error. Every screenshot frame, every behaviour test and
// every preview would otherwise have to provide one to draw a single word, and the shell overrides
// it in the one place that knows the device's locale.
val LocalTranslations: ProvidableCompositionLocal<Translations> = staticCompositionLocalOf { English }

// `Text(text = uiState.name.resolve())` — the leaf, and the last `String` in the app.
@Composable
@ReadOnlyComposable
fun TextRes.resolve(): String = LocalTranslations.current.resolve(this)
