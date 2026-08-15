package dev.fardavide.oltre.client.save.data

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
        val preferences = Preferences(galaxyLanding = MAP)

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
        assertEquals(Preferences(galaxyLanding = WORLDS), store.load())
    }

    @Test
    fun `saving twice keeps only the second`() = runTest {
        // given
        val file = FakeSaveFile()
        val store = PreferencesStore(file)

        // when
        store.save(Preferences(galaxyLanding = MAP))
        store.save(Preferences(galaxyLanding = WORLDS))

        // then
        assertEquals(Preferences(galaxyLanding = WORLDS), store.load())
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
