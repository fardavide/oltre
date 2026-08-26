package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.changelog.presentation.EnglishChangelog
import dev.fardavide.oltre.client.changelog.presentation.ItalianChangelog
import dev.fardavide.oltre.client.save.data.Preferences
import dev.fardavide.oltre.client.save.data.PreferencesStore
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

// **"It must open on game updated"**, driven through the composition root, which is the only place
// that can see it. `ChangelogGateTest` proves the rule over every combination of its three inputs;
// this proves the three inputs are the ones actually handed to it — the preferences file, the save,
// and the head of the catalogue — and that the sheet the rule raises is the changelog rather than the
// settings ladders.
//
// **The wiring is what breaks, not the rule.** A gate that answers correctly and is passed the wrong
// version, or whose answer nothing acts on, is green in every unit test there is.
class ChangelogAppBehaviourTest {

    @Test
    fun `a colony from an older build opens on the changelog`() {
        // The upgrade every player takes exactly once, and the case the third input exists for:
        // nothing is remembered, and what separates this from a fresh install is that there is a
        // colony on disk.
        // Every store here is handed in, and none of them takes the harness default: `app` opens a
        // build whose changelog has been read, precisely so that the eighteen tests about something
        // else are not driving controls behind a scrim. This class is the one that asks the question,
        // so it says what the file holds every time.
        app(saved = snapshot(), preferences = seeded(lastSeenVersion = null)) {
            assertChangelogShowing()
        }
    }

    @Test
    fun `a first launch is not a changelog`() {
        // Sixty-six pages of history before the first mine is not a welcome. The version is written
        // down anyway, so the *next* build is news — asserted below.
        app(saved = null, preferences = seeded(lastSeenVersion = null)) {
            assertChangelogShowing(showing = false)
        }
    }

    @Test
    fun `a build whose changelog has been read stays shut`() {
        val store = seeded(lastSeenVersion = running)

        app(saved = snapshot(), preferences = store) {
            assertChangelogShowing(showing = false)
        }
    }

    @Test
    fun `a build nobody has read opens even when an older one was`() {
        val store = seeded(lastSeenVersion = "0.0.1")

        app(saved = snapshot(), preferences = store) {
            assertChangelogShowing()
        }
    }

    @Test
    fun `a first launch writes the version down without showing it`() {
        // The other half of "a first launch is not a changelog": if it did not record the version,
        // the second launch of a brand new colony would open the sheet — which is the same defect
        // seen from the other side.
        val store = seeded(lastSeenVersion = null)

        app(saved = null, preferences = store) {
            assertChangelogShowing(showing = false)
        }

        assertEquals(running, runBlocking { store.load() }.lastSeenVersion)
    }

    @Test
    fun `dismissing the sheet is what marks it read`() {
        // **Dismissal rather than display**, which is the design's call and a real difference: a sheet
        // killed by a crash or a task switch has not been read, and showing it once more costs a
        // swipe where losing it costs the whole release.
        val store = seeded(lastSeenVersion = null)

        app(saved = snapshot(), preferences = store) {
            assertChangelogShowing()
            dismissTheSettings()
            assertChangelogShowing(showing = false)
        }

        assertEquals(running, runBlocking { store.load() }.lastSeenVersion)
    }

    @Test
    fun `the gear opens the ladders and the build row opens the changelog`() {
        // **The door, end to end.** The gear always opens the settings face — even here, where the
        // sheet has already been up on the other one — and the row at the foot of it crosses to the
        // changelog without stacking a second sheet.
        val store = seeded(lastSeenVersion = running)

        app(saved = snapshot(), preferences = store) {
            assertSettingsShowing(showing = false)

            openTheSettings()
            assertSettingsShowing()
            assertChangelogShowing(showing = false)

            openTheChangelog()
            assertChangelogShowing()
            assertSettingsShowing(showing = false)
        }
    }

    @Test
    fun `the Italian changelog is the one an Italian phone opens`() {
        // **The other document, driven end to end.** Everything else in this suite runs on English,
        // so without this the Italian half of the feature — the half that is a permanent obligation
        // on every future release — is only ever read by tests that compare it to English. This is
        // the one that renders it.
        //
        // The page is found by its version, which is the same in both languages; what proves the
        // document arrived is that the words on it are Italian.
        app(
            saved = snapshot(),
            preferences = seeded(lastSeenVersion = null),
            changelog = ItalianChangelog,
        ) {
            assertChangelogShowing()
            assertReads(ItalianChangelog.releases.first().headline)
            assertDoesNotRead(EnglishChangelog.releases.first().headline)
        }
    }

    @Test
    fun `the galaxy landing survives the changelog writing its version`() {
        // Two fields, one file, and the write that lands second must not drop the one that landed
        // first. This is the case the defaulted record in `PreferencesStore` exists for, driven end
        // to end rather than at the store.
        val store = seeded(lastSeenVersion = null, galaxyLanding = "WORLDS")

        app(saved = snapshot(), preferences = store) {
            dismissTheSettings()
        }

        val saved = runBlocking { store.load() }
        assertEquals("WORLDS", saved.galaxyLanding)
        assertEquals(running, saved.lastSeenVersion)
    }

    // The version this build is, read the way the app reads it: the head of the catalogue. A literal
    // here would be a second place to remember on every release.
    private val running: String get() = EnglishChangelog.releases.first().version.printed

    private fun seeded(lastSeenVersion: String?, galaxyLanding: String? = null): PreferencesStore {
        val store = PreferencesStore(InMemorySaveFile())
        runBlocking {
            store.save(
                Preferences(
                    galaxyLanding = galaxyLanding,
                    lastSeenVersion = lastSeenVersion,
                    provider = null,
                ),
            )
        }
        return store
    }

    private fun snapshot(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        state = GameState.initial(GalaxySeed(20_260_807L)),
    )
}
