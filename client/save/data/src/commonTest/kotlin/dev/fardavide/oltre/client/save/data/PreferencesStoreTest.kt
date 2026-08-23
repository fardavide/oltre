package dev.fardavide.oltre.client.save.data

import dev.fardavide.oltre.core.NotificationCategory
import dev.fardavide.oltre.core.NotificationGrouping
import dev.fardavide.oltre.core.NotificationScope
import dev.fardavide.oltre.core.NotificationSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesStoreTest {

    @Test
    fun `a store with no file yet answers the empty preferences`() = runTest {
        // given
        val store = PreferencesStore(FakeSaveFile())

        // when / then
        assertEquals(Preferences.NONE, store.load())
    }

    @Test
    fun `what is saved is what loads back`() = runTest {
        // given
        val store = PreferencesStore(FakeSaveFile())
        val preferences = Preferences(galaxyLanding = MAP, notifications = null)

        // when
        store.save(preferences)

        // then
        assertEquals(preferences, store.load())
    }

    @Test
    fun `a file that is not JSON at all answers the empty preferences`() = runTest {
        // given a file a player's backup tool or a half-finished write left unreadable
        val store = PreferencesStore(FakeSaveFile("not preferences"))

        // when / then
        assertEquals(Preferences.NONE, store.load())
    }

    @Test
    fun `a file whose JSON is missing the landing field answers the empty preferences`() = runTest {
        // given
        val store = PreferencesStore(FakeSaveFile("""{}"""))

        // when / then
        assertEquals(Preferences.NONE, store.load())
    }

    @Test
    fun `a file carrying a field this build does not know still answers the landing it does know`() = runTest {
        // given a file written by a later build that remembers something this one has never heard of
        val store = PreferencesStore(FakeSaveFile("""{"galaxyLanding":"$WORLDS","lastTabOpened":"COLONY"}"""))

        // when / then
        assertEquals(Preferences(galaxyLanding = WORLDS, notifications = null), store.load())
    }

    // **The field that had to be nullable, and this is the test that says why.** This record carries
    // no schema version and cannot migrate, so a *required* field added today would make every
    // preferences file already on disk fail to decode — and `load` answers `NONE` to a failure, so a
    // player would have lost their galaxy landing to a settings screen they had not opened yet.
    @Test
    fun `a file written before the settings existed still answers the landing it knows`() = runTest {
        // given the exact shape every file on disk has today
        val store = PreferencesStore(FakeSaveFile("""{"galaxyLanding":"$WORLDS"}"""))

        // when / then — the landing survives and the settings are simply unchosen
        assertEquals(Preferences(galaxyLanding = WORLDS, notifications = null), store.load())
    }

    @Test
    fun `what the player chose about alerts survives a save and a load`() = runTest {
        // given a player who moved both settings and muted a category
        val store = PreferencesStore(FakeSaveFile())
        val preferences = Preferences(
            galaxyLanding = MAP,
            notifications = NotificationSettings(
                scope = NotificationScope.BY_CATEGORY,
                grouping = NotificationGrouping.SUMMARY,
                categories = NotificationCategory.entries.toSet() - NotificationCategory.PROBES,
            ),
        )

        // when
        store.save(preferences)

        // then
        assertEquals(preferences, store.load())
    }

    @Test
    fun `saving twice keeps only the second`() = runTest {
        // given
        val file = FakeSaveFile()
        val store = PreferencesStore(file)

        // when
        store.save(Preferences(galaxyLanding = MAP, notifications = null))
        store.save(Preferences(galaxyLanding = WORLDS, notifications = null))

        // then
        assertEquals(Preferences(galaxyLanding = WORLDS, notifications = null), store.load())
        assertEquals(2, file.writeCount)
    }

    private companion object {

        // Bare names rather than an enum, because the enum they stand for lives in
        // `:client:galaxy:presentation` and this module cannot see it — the same reason
        // `Preferences.galaxyLanding` is a `String?`. What matters here is that the store carries a
        // name through untouched; whether that name resolves to a view is the composition root's
        // question, and its tests are where it is asked.
        const val MAP = "MAP"
        const val WORLDS = "WORLDS"
    }
}
