package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// The heading over a list of rows. Both screens had their own copy, identical in every token —
// colour, family, size, weight, letter spacing, padding — and differing only in that Research
// appends a rule to its heading ("TECHNOLOGIES · one project at a time"). That made them two
// variants of one component rather than two components, so they are one here, and the rule is
// the optional half.
//
// The bare case deliberately does *not* wrap in the Row. A Row around a single Text almost
// certainly measures the same, but "almost certainly measures the same" is a claim this
// extraction is not allowed to make: the Colony's baselines have to be untouched by it, and the
// only way to be sure of that is to emit exactly what they were recorded from.
@Composable
fun SectionLabel(text: String, rule: String? = null) {
    if (rule == null) {
        Text(
            text = text,
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(bottom = 9.dp, start = 2.dp),
        )
    } else {
        val mono = oltreMono()
        Row(modifier = Modifier.padding(bottom = 9.dp, start = 2.dp)) {
            Text(
                text = text,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = " · $rule",
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }
    }
}
