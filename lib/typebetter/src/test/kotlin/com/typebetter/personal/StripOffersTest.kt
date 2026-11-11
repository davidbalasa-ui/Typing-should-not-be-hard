package com.typebetter.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StripOffersTest {
    @Test
    fun `the first undo offers to keep the word`() {
        val (rules, offers) = StripOffers.afterUndo(PersonalRules(), "Kasmyra", "Kashmir", isMyWord = false)
        assertEquals(listOf<StripOffer>(StripOffer.AddWord("Kasmyra")), offers)
        assertEquals(1, rules.revertCounts.values.single())
    }

    @Test
    fun `the second undo of the same correction also asks to stop it`() {
        val (once, _) = StripOffers.afterUndo(PersonalRules(), "its", "it's", isMyWord = false)
        val (_, offers) = StripOffers.afterUndo(once, "its", "it's", isMyWord = false)
        assertEquals(StripOffer.StopChanging("its", "it's"), offers.first())
    }

    @Test
    fun `a word already on My words is not offered again`() {
        val (_, offers) = StripOffers.afterUndo(PersonalRules(), "Xylos", "Xylose", isMyWord = true)
        assertTrue(offers.isEmpty())
    }

    @Test
    fun `an unknown finished word is offered, a known one is not`() {
        assertEquals(StripOffer.AddWord("Zephaniah"), StripOffers.afterWord("Zephaniah ", isKnown = false, isMyWord = false))
        assertNull(StripOffers.afterWord("house", isKnown = true, isMyWord = false))
        assertNull(StripOffers.afterWord("Xylos", isKnown = false, isMyWord = true))
    }

    @Test
    fun `links, addresses and scraps are never offered`() {
        for (word in listOf("a", "zz", "me@x.com", "http://x", "12345", "-", "x/y", "word,")) {
            assertFalse(StripOffers.looksAddable(word), word)
        }
        for (word in listOf("DM", "Next.js", "Ba'al", "Auth0", "WeaveMaker")) {
            assertTrue(StripOffers.looksAddable(word), word)
        }
    }
}

class StripOffersAskOnceTest {
    @Test
    fun `the stop question is shown once even when it is not answered`() {
        var rules = PersonalRules()
        val asked = (1..4).map {
            val (next, offers) = StripOffers.afterUndo(rules, "its", "it's", isMyWord = false)
            rules = next
            offers.any { it is StripOffer.StopChanging }
        }
        assertEquals(listOf(false, true, false, false), asked)
    }

    @Test
    fun `an offer names its word`() {
        assertEquals("Kasmyra", StripOffer.AddWord("Kasmyra").word)
        assertEquals("its", StripOffer.StopChanging("its", "it's").word)
    }
}
