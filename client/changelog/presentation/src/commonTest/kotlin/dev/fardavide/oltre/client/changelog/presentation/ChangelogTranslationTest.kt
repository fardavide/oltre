package dev.fardavide.oltre.client.changelog.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **What stands in for the compiler.** The global catalogue makes a second language a compile-time
// obligation through an exhaustive `when` over `StringId`; a document cannot do that, so this does —
// and it catches strictly more, which is the argument `.claude/docs/changelog-sheet.md` §4 makes for
// the shape. A `when` can only tell you an id is missing. This tells you a release Italian never got,
// a date that drifted between two files, and a page that lost a line in translation.
class ChangelogTranslationTest {

    @Test
    fun `every language carries the same releases in the same order`() {
        for ((language, changelog) in everyChangelog) {
            assertEquals(
                EnglishChangelog.releases.map { it.version },
                changelog.releases.map { it.version },
                "$language does not carry the same run",
            )
        }
    }

    @Test
    fun `a release is dated the same in every language`() {
        for ((language, changelog) in everyChangelog) {
            assertEquals(
                EnglishChangelog.releases.map { it.date },
                changelog.releases.map { it.date },
                "$language dates a release differently",
            )
        }
    }

    @Test
    fun `a page says as many things in every language`() {
        // The one a translator actually gets wrong: three notes in English and two in Italian is a
        // page that quietly says less, and both pages look complete on their own.
        for ((language, changelog) in everyChangelog) {
            assertEquals(
                EnglishChangelog.releases.map { it.notes.size },
                changelog.releases.map { it.notes.size },
                "$language drops or adds a line on some page",
            )
        }
    }

    @Test
    fun `no page is left in English by mistake`() {
        // A release copied across and not translated is the failure this whole shape is exposed to,
        // and it is invisible in a diff of sixty-five entries. Compared by headline because a note
        // can legitimately coincide — a version number or a name is the same in both languages — and
        // a headline is a sentence.
        val english = EnglishChangelog.releases.associate { it.version to it.headline }
        val untranslated = ItalianChangelog.releases.filter { english[it.version] == it.headline }

        assertTrue(
            untranslated.isEmpty(),
            "still in English: ${untranslated.map { it.version.printed }}",
        )
    }

    @Test
    fun `the run is newest first and has no repeats`() {
        // What the sheet assumes everywhere: page 0 is today, the rail's left end is it, and
        // `toBuildRowUiState` reads `first()` for the version the player is running.
        for ((language, changelog) in everyChangelog) {
            val versions = changelog.releases.map { it.version }

            assertEquals(versions.sortedDescending(), versions, "$language is not newest first")
            assertEquals(versions.distinct().size, versions.size, "$language lists a release twice")
        }
    }

    @Test
    fun `a date never runs ahead of a later release`() {
        // Several releases share a day — five went out on 2026-08-10 — so this is not strictly
        // decreasing. What it forbids is a date that goes *forwards* as the versions go back.
        for ((language, changelog) in everyChangelog) {
            val dates = changelog.releases.map { it.date }
            for (index in 1 until dates.size) {
                assertTrue(
                    dates[index] <= dates[index - 1],
                    "$language: ${changelog.releases[index].version.printed} is dated after the release above it",
                )
            }
        }
    }
}
