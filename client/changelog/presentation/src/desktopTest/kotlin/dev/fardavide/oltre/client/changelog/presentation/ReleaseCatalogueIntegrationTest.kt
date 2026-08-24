package dev.fardavide.oltre.client.changelog.presentation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The catalogue against the repository.** Sixty-five hand-written entries and a permanent
// per-release obligation is exactly the shape that rots quietly, so this reads the two files that
// already have to be right — the README's changelog and the version catalogue — and asserts the
// in-game changelog agrees with both.
//
// What it makes impossible, and each of these is a real way to ship the wrong thing:
//
// - **A version bumped with no page.** The sheet would open on the release before it, or not open at
//   all, and everything would look fine in every other test.
// - **A page for a release that never shipped**, or a date that drifted from the one the README
//   carries — the caption is the only thing on the page nobody can check by eye.
// - **A release dropped from the middle**, which is the failure a `size` assertion would miss.
//
// An integration test rather than a unit one, and the name says so: it reads files off disk. The
// root arrives as a system property from this module's build file, because a test's working
// directory is its own module.
class ReleaseCatalogueIntegrationTest {

    @Test
    fun `the changelog carries exactly the releases the README does`() {
        assertEquals(
            readmeReleases().map { it.first },
            EnglishChangelog.releases.map { it.version.printed },
        )
    }

    @Test
    fun `a release is dated the day the README says it shipped`() {
        assertEquals(
            readmeReleases(),
            EnglishChangelog.releases.map { it.version.printed to it.date.toString() },
        )
    }

    @Test
    fun `the newest page is the version this build is`() {
        // **The one that makes a forgotten page a failing build rather than a silent sheet.** The
        // catalogue's head *is* the running version — there is no generated `BuildConfig` in this
        // build and one string does not earn source generation — so if the two disagree, the
        // changelog would never raise itself and the settings row would name a release nobody is
        // running.
        assertEquals(gradleVersion(), EnglishChangelog.releases.first().version.printed)
    }

    // `### 0.18.0 — 2026-08-23`, in the order the README lists them, which is newest first. The
    // em dash is the README's own; a hyphen here would match nothing and the test would report an
    // empty changelog rather than a mismatched one — hence the guard below.
    private fun readmeReleases(): List<Pair<String, String>> {
        val headings = HEADING.findAll(repoFile("README.md").readText())
            .map { match -> match.groupValues[1] to match.groupValues[2] }
            .toList()

        assertTrue(headings.isNotEmpty(), "no release headings found in README.md")
        return headings
    }

    private fun gradleVersion(): String {
        val toml = repoFile("gradle/libs.versions.toml").readText()
        val match = requireNotNull(VERSION.find(toml)) { "no `oltre` version in gradle/libs.versions.toml" }
        return match.groupValues[1]
    }

    private fun repoFile(path: String): File {
        val root = requireNotNull(System.getProperty("oltre.rootDir")) {
            "oltre.rootDir is not set — see this module's build file"
        }
        val file = File(root, path)
        assertTrue(file.isFile, "$path is not where this test expects the repository to be")
        return file
    }

    private companion object {

        val HEADING = Regex("""^### (\d+\.\d+\.\d+) — (\d{4}-\d{2}-\d{2})$""", RegexOption.MULTILINE)
        val VERSION = Regex("""^oltre = "(\d+\.\d+\.\d+)"$""", RegexOption.MULTILINE)
    }
}
