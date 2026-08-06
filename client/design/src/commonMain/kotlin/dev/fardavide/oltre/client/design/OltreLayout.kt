package dev.fardavide.oltre.client.design

import androidx.compose.ui.unit.dp

// The window is never assumed to be a phone. iPad (Split View, Stage Manager) and desktop hand
// the UI arbitrary widths, so screens cap their content and centre it rather than stretching:
// the design is a single column of cards, and a 1000dp-wide row of 13.5sp text is unreadable.
object OltreLayout {

    // A little wider than the largest phone (430dp on a Pro Max), so no phone ever hits the cap
    // and renders differently from the mockup.
    val maxContentWidth = 560.dp
}
