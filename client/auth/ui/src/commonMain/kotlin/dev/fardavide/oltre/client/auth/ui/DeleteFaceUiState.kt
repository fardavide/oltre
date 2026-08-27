package dev.fardavide.oltre.client.auth.ui

import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.design.text.TextRes

// **The settings sheet's third face, in the one record both of its faces are drawn from.** The
// warning face and the last step are the same layout with different contents, which is what lets them
// swap in place — 210ms, exactly as the changelog does, with no sheet over a sheet and no back stack.
//
// A flat record rather than a sealed pair, for `AlertSheetUiState`'s reason one sheet along: the two
// faces are two *values*, produced by two mapper functions that a test can hold to their invariants,
// and a sealed pair would be the drawing written twice to say the same thing.
data class DeleteFaceUiState(
    val title: TextRes,
    val intro: TextRes,
    // **Empty on the last step, and that is the design's own move.** The four rows are for reading and
    // the last face is for deciding, so what is left above the question is empty — which is the shape
    // of what the tap does.
    val facts: List<DeleteFactUiState>,
    // The fact the numbers cannot teach, and the one Apple's requirement does not ask for: signing in
    // again with the same account starts an empty colony.
    val second: TextRes,
    // **Deleting an account needs the network, so it refuses exactly as a dispatch does.** Null with
    // signal. Red for the refusal and red for the action, and they do not collide because one is a
    // sentence and the other is a control.
    val refusal: RefusalUiState?,
    // Null on the warning face, where there is nothing to keep yet — the handle is the way out and
    // dismissal is a no. Present on the last step, first in the row and the wider read.
    val keep: TextRes?,
    val action: TextRes,
    // The warning face's action is a red **ghost**, which in this system is an action that is not yet
    // the action; the last step carries the only filled red button in the product.
    val destructive: Boolean,
)

data class DeleteFactUiState(val label: TextRes, val value: TextRes)

object DeleteTestTags {

    const val FACE = "delete.face"
    const val ACTION = "delete.action"
    const val KEEP = "delete.keep"
    const val REFUSAL = "delete.refusal"
    const val ROW = "settings.account.delete"
}
