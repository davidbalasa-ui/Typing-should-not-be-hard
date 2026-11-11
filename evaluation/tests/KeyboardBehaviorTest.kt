import dev.patrickgold.florisboard.dictate.field.FieldText
import dev.patrickgold.florisboard.dictate.field.TranscriptHandover
import dev.patrickgold.florisboard.dictate.symbols.*
import dev.patrickgold.florisboard.ime.nlp.latin.*
import org.junit.Assert.*
import org.junit.Test

/** Additional user-focused checks against real upstream implementations. No Android mocks. */
class KeyboardBehaviorTest {
    @Test fun correctionRejectsMissingTouchEvidence() {
        AutoCorrectStrength.entries.forEach { assertFalse(AutoCommitGate.allows(0f, 0, it)) }
    }
    @Test fun cautiousModePreservesMoreAmbiguousInput() {
        assertFalse(AutoCommitGate.allows(1f, 5, AutoCorrectStrength.CAUTIOUS))
        assertTrue(AutoCommitGate.allows(1f, 5, AutoCorrectStrength.BALANCED))
    }
    @Test fun aggressiveModeAcceptsCostsBalancedRejects() {
        assertFalse(AutoCommitGate.allows(2f, 5, AutoCorrectStrength.BALANCED))
        assertTrue(AutoCommitGate.allows(2f, 5, AutoCorrectStrength.AGGRESSIVE))
    }
    @Test fun symbolRemappingChangesOnlyTheChosenKey() {
        val initial = CustomSymbols()
        val replacement = SymbolKey(SymbolValue.Text("∑"))
        val changed = initial.with(1, 5, replacement)
        assertEquals(replacement, changed.key(1, 5))
        assertEquals(initial.key(1, 6), changed.key(1, 6))
        assertEquals(initial, changed.with(1, 5, null))
    }
    @Test fun symbolRemappingKeepsRowGeometry() {
        val changed = CustomSymbols().with(0, 0, SymbolKey(SymbolValue.Text("->")))
        assertEquals(listOf(10, 7), changed.rows(0).map { it.size })
        assertEquals(listOf(10, 10, 7), changed.rows(1).map { it.size })
    }
    @Test fun emojiJoinersAndVariationSelectorsSurviveSymbolCleaning() {
        assertEquals("👨‍👩‍👧‍👦", SymbolText.clean("👨‍👩‍👧‍👦"))
        assertEquals("❤️", SymbolText.clean("❤️"))
    }
    @Test fun pastedTechnicalTextKeepsAllSymbols() {
        val text = "Kas → Reygar; DC16 CON; https://example.com/a?x=1&y=2"
        assertEquals(text, FieldText("").insert(text).text)
    }
    @Test fun replacingASelectionKeepsSurroundingText() {
        val field = FieldText("Hello Gordon!", 12, 6..12)
        assertEquals("Hello Niah!", field.insert("Niah").text)
    }
    @Test fun suggestionDoesNotEraseTrailingPunctuation() {
        assertEquals("Hello, Rory!", FieldText("Helo, Rory!", 4).replaceWord("Hello").text)
    }
    @Test fun dictationPreviewPreservesTextAfterCursor() {
        val field = FieldText("Meet Rory tomorrow", 9)
        assertEquals("Meet Niah tomorrow", field.replaceBeforeCursor(4, "Niah").text)
    }
    @Test fun dictationContinuesAfterPreviouslyHandedOverText() {
        assertEquals("tomorrow", TranscriptHandover.continuation("Meet Rory", "Meet Rory tomorrow"))
    }
    @Test fun unchangedDictationDoesNotRepeatHandedOverText() {
        assertEquals("", TranscriptHandover.continuation("Meet Rory", "Meet Rory"))
    }
    @Test fun partialWordDoesNotReappearAsATrailingFragment() {
        assertEquals("tomorrow", TranscriptHandover.continuation("Meet Ro", "Meet Rory tomorrow"))
    }
}
