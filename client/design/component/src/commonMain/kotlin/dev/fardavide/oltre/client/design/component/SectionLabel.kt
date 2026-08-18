package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// The heading over a list of rows. Both screens had their own copy, identical in every token —
// colour, family, size, weight, letter spacing, padding — and differing only in that Research
// appends a rule to its heading ("TECHNOLOGIES · one project at a time"). That made them two
// variants of one component rather than two components, so they are one here, and the rule is
// the optional half.
//
// The bare case deliberately does *not* wrap in the Row. A Row around a single Text almost
// certainly measures the same, but "almost certainly measures the same" is a claim the extraction
// was not allowed to make: the Colony's baselines had to be untouched by it, and the only way to
// be sure of that is to emit exactly what they were recorded from. That still holds for the
// layout, and the depth pass does not change it.
//
// What the depth pass does change is the colour, in *both* branches: the label sits at
// textSecondary now. One label reading two different greys depending on whether it happens to
// carry a trailing clause is the kind of drift this component exists to prevent, so the lift is
// not conditional on the rule — which is why the Colony and the galaxy bands move too.
@Composable
fun SectionLabel(text: TextRes, rule: TextRes? = null) {
    if (rule == null) {
        Text(
            text = text.resolve(),
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(bottom = 9.dp, start = 2.dp),
        )
    } else {
        val mono = oltreMono()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp, start = 2.dp),
        ) {
            Text(
                text = text.resolve(),
                color = OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            // The rule replaces the middot that used to join these two. A separator glyph and a
            // line drawn between them say the same thing, and only one of them turns two
            // paragraphs of grey into two bands.
            Box(
                modifier = Modifier
                    .padding(horizontal = 7.dp)
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.09f)),
            )
            Text(
                text = rule.resolve(),
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }
    }
}
