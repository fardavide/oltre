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
        val preferences = Preferences(galaxyLanding = MAP, lastSeenVersion = "0.19.0", provider = null)

        // when
        store.save(preferences)

        // then
        assertEquals(preferences, store.load())
    }

    @Test
    fun `a file written before the changelog existed keeps the landing it had`() = runTest {
        // The upgrade every player takes exactly once. A field added to this record must never cost
        // the fields already in the file — which is why the store decodes a record with defaults
        // rather than this one directly.
        val store = PreferencesStore(FakeSaveFile("""{"galaxyLanding":"$WORLDS"}"""))

        assertEquals(Preferences(galaxyLanding = WORLDS, lastSeenVersion = null, provider = null), store.load())
    }

    @Test
    fun `a file that remembers only a version is a colony that never chose a landing`() = runTest {
        // The other direction of the same tolerance: the fields are independent and either may be
        // absent.
        val store = PreferencesStore(FakeSaveFile("""{"lastSeenVersion":"0.19.0"}"""))

        assertEquals(Preferences(galaxyLanding = null, lastSeenVersion = "0.19.0", provider = null), store.load())
    }

    @Test
    fun `a file that is not JSON at all answers the empty preferences`() = runTest {
        // given a file a player's backup tool or a half-finished write left unreadable
        val store = PreferencesStore(FakeSaveFile("not preferences"))

        // when / then
        assertEquals(Preferences.NONE, store.load())
    }

    @Test
    fun `a file whose JSON carries no field at all answers the empty preferences`() = runTest {
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
        assertEquals(Preferences(galaxyLanding = WORLDS, lastSeenVersion = null, provider = null), store.load())
    }

    @Test
    fun `saving twice keeps only the second`() = runTest {
        // given
        val file = FakeSaveFile()
        val store = PreferencesStore(file)

        // when
        store.save(Preferences(galaxyLanding = MAP, lastSeenVersion = null, provider = null))
        store.save(Preferences(galaxyLanding = WORLDS, lastSeenVersion = null, provider = null))

        // then
        assertEquals(Preferences(galaxyLanding = WORLDS, lastSeenVersion = null, provider = null), store.load())
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
