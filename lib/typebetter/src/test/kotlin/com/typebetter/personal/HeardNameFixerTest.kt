package com.typebetter.personal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeardNameFixerTest {
    /** The English word list the keyboard ships, folded to frequency (128..255). */
    private val dictionary: Map<String, Int> by lazy {
        val file = listOf("../../app/src/main/assets/ime/dict/en.json", "app/src/main/assets/ime/dict/en.json")
            .map { File(it) }.first { it.exists() }
        Regex("\"([^\"]+)\":\\s*(\\d+)").findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
    }
    private val frequency: (String) -> Int? = { dictionary[it] }

    private val fixer = HeardNameFixer(StarterPack.words)

    private fun fix(text: String) = fixer.apply(text, frequency)

    @Test
    fun `Naya is written as Niah, mid-sentence and at a sentence start`() {
        // David, 2026-11-25: "names like Niah are written as Naya when I talk".
        assertEquals("I talked to Niah today.", fix("I talked to Naya today."))
        assertEquals("Niah is coming. Then Niah left!", fix("Naya is coming. Then Naya left!"))
        assertEquals("Niah's sword", fix("Naya's sword"))
        assertEquals("\"Niah,\" she said.", fix("\"Naya,\" she said."))
    }

    @Test
    fun `spellings the model invents are put back`() {
        assertEquals("Zephaniah and Zephaniah went", fix("Zephania and Zefaniah went"))
        assertEquals("Strahd laughed", fix("Strad laughed"))
        assertEquals("Ask Kasmyra", fix("Ask Kasmira"))
    }

    @Test
    fun `real words are left alone`() {
        // Lower case: the model did not write it as a name (the Xylos/silos rule from typing).
        assertEquals("The silos were full.", fix("The silos were full."))
        assertEquals("naya said hi", fix("naya said hi"))
        // A sentence start gives every word a capital, so only very rare words count there.
        assertEquals("Silos are full.", fix("Silos are full."))
        // Common words never change, whatever their case.
        assertEquals("Now is soon. Noah came.", fix("Now is soon. Noah came."))
    }

    @Test
    fun `My words themselves and contractions are not touched`() {
        assertEquals("Niah and Nia's friend Kas don't", HeardNameFixer(listOf("Niah", "Nia", "Kas")).apply("Niah and Nia's friend Kas don't", frequency))
    }

    @Test
    fun `two My words that sound alike are never guessed between`() {
        val both = HeardNameFixer(listOf("Niah", "Nyah"))
        assertEquals("I met Naya", both.apply("I met Naya", frequency))
    }

    @Test
    fun `a Never change entry blocks the swap`() {
        val rules = PersonalRules().withNeverChange(NeverChange("Naya", "Niah"))
        assertEquals("I met Naya", HeardNameFixer(listOf("Niah"), rules).apply("I met Naya", frequency))
    }

    @Test
    fun `capitals follow the heard word only when shouted`() {
        assertEquals("NIAH WAS HERE", fix("NYAH WAS HERE"))
        // Shouting is not a name: a real word in capitals stays.
        assertEquals("NAYA WAS HERE", fix("NAYA WAS HERE"))
        assertEquals("met Niah", HeardNameFixer(listOf("Niah")).apply("met nyah", frequency))
    }

    @Test
    fun `acronyms and short words are not sound-matched`() {
        assertNull(SoundKey.of("PC"))
        assertNull(SoundKey.of("Noah"))
        assertEquals("the AC is on", fix("the AC is on"))
    }

    @Test
    fun `sound keys`() {
        assertEquals(SoundKey.of("Niah"), SoundKey.of("Naya"))
        assertEquals(SoundKey.of("Xylos"), SoundKey.of("silos"))
        assertEquals(SoundKey.of("Zephaniah"), SoundKey.of("Zefania"))
        assertNotEquals(SoundKey.of("Kasmyra"), SoundKey.of("Kashmira"))
    }

    @Test
    fun `nothing changes without My words, or in an empty transcript`() {
        assertEquals("I met Naya", HeardNameFixer(emptyList()).apply("I met Naya", frequency))
        assertEquals("", fix(""))
    }

    @Test
    fun `few real words ever change, and never a common or lower-case one`() {
        // Every rare dictionary word, written as a name in the middle of a sentence: the worst case.
        var changedAsName = 0
        for ((word, freq) in dictionary) {
            if (!word.all { it in 'a'..'z' }) continue
            val lower = "we saw $word there"
            assertEquals(lower, fix(lower), "lower-case $word")
            val name = "we saw ${word.replaceFirstChar { it.uppercase() }} there"
            if (fix(name) != name) {
                assertTrue(freq < HeardNameFixer.MAX_FREQ_NAME, "common word $word changed")
                changedAsName++
            }
        }
        // 24 on 2026-11-25 (Layla → Lyla, Kass → Kas, Silos → Xylos...), out of about 29,000 rare words.
        assertTrue(changedAsName <= 40, "rare words changed when capitalised: $changedAsName")
    }
}
