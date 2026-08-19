package dev.fardavide.oltre.client.design.text

import kotlin.test.Test
import kotlin.test.assertEquals

// **How the game chooses its language, and the whole of that decision.** Davide settled it on
// 2026-08-16: the system locale, no picker, no settings surface — so what is left to get right is a
// tag-to-table mapping, and this is it.
//
// Deliberately coarse. Region is dropped rather than consulted: there is no per-region catalogue and
// there should not be one, so `it-CH` is the same Italian as `it-IT` and asking a Swiss player to
// read a different table would be inventing a difference nobody asked for.
class TranslationsForTest {

    @Test
    fun `should speak Italian to an Italian device`() {
        assertEquals(Italian, translationsFor("it"))
    }

    @Test
    fun `should ignore the region on an Italian tag`() {
        assertEquals(Italian, translationsFor("it-IT"))
        assertEquals(Italian, translationsFor("it-CH"))
        assertEquals(Italian, translationsFor("it_IT"))
    }

    // A tag arrives from the platform, and the two platforms do not agree on its case: Android hands
    // back what the user set and iOS normalises. Neither is worth trusting.
    @Test
    fun `should ignore the case of the tag`() {
        assertEquals(Italian, translationsFor("IT"))
        assertEquals(Italian, translationsFor("It-it"))
    }

    // **English is the fallback rather than a language with a tag of its own**, which is what makes
    // this total: a locale nobody has translated gets the table that is always complete, and there is
    // no missing-translation state to design because there is no way to reach one.
    @Test
    fun `should fall back to English for every other language`() {
        assertEquals(English, translationsFor("en"))
        assertEquals(English, translationsFor("en-GB"))
        assertEquals(English, translationsFor("de"))
        assertEquals(English, translationsFor("ja"))
    }

    // Not defensive so much as honest about where the tag comes from: it is read off a device, and a
    // device with no language set is a case a colony should survive.
    @Test
    fun `should fall back to English for a tag that says nothing`() {
        assertEquals(English, translationsFor(""))
        assertEquals(English, translationsFor("-IT"))
    }

    // "italian" is not a language tag, and neither is anything else that merely starts with the two
    // letters. Matching on the subtag rather than on a prefix is what keeps `ita` — a valid ISO 639-2
    // code that no platform hands back here — from quietly meaning something.
    @Test
    fun `should match the language subtag rather than a prefix`() {
        assertEquals(English, translationsFor("ita"))
        assertEquals(English, translationsFor("itl-IT"))
    }
}
