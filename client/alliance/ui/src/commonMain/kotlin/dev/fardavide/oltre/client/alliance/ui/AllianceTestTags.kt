package dev.fardavide.oltre.client.alliance.ui

// **What a robot reaches for.** `internal` until the screens landed, because the one thing on this
// destination was a sentence nothing drove; a behaviour test in `:client:shell` has to name the
// controls it presses, so they are public now and the names are part of the module's surface.
object AllianceTestTags {
    const val SCREEN = "alliance-screen"
    const val TITLE = "alliance-coming-soon-title"
    const val BODY = "alliance-coming-soon-body"

    // ── Searching and founding ───────────────────────────────────────────────────────────────
    const val SEARCH_FIELD = "alliance-search-field"
    const val SEARCH_LINE = "alliance-search-line"
    const val SEARCH_ROW = "alliance-search-row"
    const val FOUND_NAME = "alliance-found-name"
    const val FOUND_TAG = "alliance-found-tag"
    const val FOUND_TAG_RULE = "alliance-found-tag-rule"
    const val FOUND_PRICE = "alliance-found-price"
    const val FOUND_SHORT = "alliance-found-short"
    const val FOUND_ACTION = "alliance-found-action"
    const val FOUND_NAME_REFUSAL = "alliance-found-name-refusal"
    const val FOUND_TAG_REFUSAL = "alliance-found-tag-refusal"

    // ── Waiting ──────────────────────────────────────────────────────────────────────────────
    const val WAITING_LINE = "alliance-waiting-line"
    const val WITHDRAW = "alliance-withdraw"

    // ── Enlisted ─────────────────────────────────────────────────────────────────────────────
    const val HEAD_NAME = "alliance-head-name"
    const val HEAD_SEATS = "alliance-head-seats"
    const val HEAD_LEVEL = "alliance-head-level"
    const val PENDING_ROW = "alliance-pending-row"
    const val PENDING_EMPTY = "alliance-pending-empty"
    const val ACCEPT = "alliance-accept"
    const val DECLINE = "alliance-decline"
    const val ROSTER_ROW = "alliance-roster-row"
    const val POOL_ROW = "alliance-pool-row"
    const val TREASURY_RULE = "alliance-treasury-rule"
    const val CONTRIBUTED = "alliance-contributed"
    const val SHARE = "alliance-share"
    const val CONTRIBUTE_BASKET = "alliance-contribute-basket"
    const val CONTRIBUTE_ACTION = "alliance-contribute-action"
    const val CONTRIBUTE_SHORT = "alliance-contribute-short"
    const val CONFIRM_CONTRIBUTE = "alliance-confirm-contribute"
    const val KEEP_CONTRIBUTE = "alliance-keep-contribute"
    const val PROJECT_ROW = "alliance-project-row"
    const val PROJECT_BUY = "alliance-project-buy"
    const val DEPARTURE = "alliance-departure"

    // ── The member commands ──────────────────────────────────────────────────────────────────
    const val MEMBER_FACE = "alliance-member-face"
    const val MEMBER_NAME = "alliance-member-name"
    const val MEMBER_ROLE = "alliance-member-role"
    const val MEMBER_READING = "alliance-member-reading"
    const val MEMBER_ROLE_ACTION = "alliance-member-role-action"
    const val MEMBER_ROLE_NOTE = "alliance-member-role-note"
    const val MEMBER_KICK = "alliance-member-kick"
    const val MEMBER_CONSEQUENCE = "alliance-member-consequence"
    const val MEMBER_AFTERMATH = "alliance-member-aftermath"
    const val MEMBER_KICK_CONFIRM = "alliance-member-kick-confirm"
    const val MEMBER_KEEP = "alliance-member-keep"
    const val MEMBER_HELD = "alliance-member-held"

    // ── The whole face with no signal ────────────────────────────────────────────────────────
    const val HELD = "alliance-held"

    // **What an element nobody drives carries.** The small helpers on this screen take a tag rather
    // than a nullable one, because an optional tag is a branch and a branch only one caller ever
    // takes is a branch nothing can cover — so every element gets a tag and the ones that are not
    // targets say so by sharing this one.
    const val UNNAMED = "alliance-unnamed"

    // A row's tag, suffixed so a robot can press the third one. Composed here rather than at each
    // call site so the separator cannot differ between the screen and the test that drives it.
    fun row(tag: String, index: Int): String = "$tag-$index"
}
