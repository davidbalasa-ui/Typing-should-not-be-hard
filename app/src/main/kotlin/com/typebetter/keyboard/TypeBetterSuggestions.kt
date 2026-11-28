package com.typebetter.keyboard

import android.content.Context
import com.typebetter.personal.StripOffer
import com.typebetter.personal.word
import com.typebetter.personal.keyOf
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.devtools.flogError

/**
 * Where TypeBetter's rules meet the keyboard's suggestion list. Called from `NlpManager`, kept here so
 * the upstream file only gains a few lines.
 */
object TypeBetterSuggestions {
    /**
     * Runs the personal rules over the keyboard's suggestions for the word being typed ([typed]).
     * Never throws: if anything goes wrong, the keyboard's own suggestions are returned unchanged.
     *
     * @param isDictionaryWord whether a word (given in lower case) is in the keyboard's dictionary.
     */
    suspend fun shape(
        typed: String,
        stock: List<SuggestionCandidate>,
        /** The keyboard's "Auto-correct" setting; off means no TypeBetter rule swaps a word on Space. */
        autoCorrect: Boolean,
        isDictionaryWord: suspend (String) -> Boolean,
    ): List<SuggestionCandidate> {
        if (typed.isBlank()) return stock
        return try {
            val key = keyOf(typed)
            // Asked once, here, because the rules run synchronously and only ever ask about the typed word.
            val typedIsCommon = try {
                isDictionaryWord(key)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
            val entries = PersonalWords.typingRules().apply(
                typed = typed,
                stock = stock,
                textOf = { it.text.toString() },
                autoCommitOf = { it.isEligibleForAutoCommit },
                isCommonWord = { it == key && typedIsCommon },
                autoCorrect = autoCorrect,
            )
            entries.map { entry ->
                val original = entry.stock
                when {
                    original != null && original.isEligibleForAutoCommit == entry.autoCommit -> original
                    original is WordSuggestionCandidate -> original.copy(isEligibleForAutoCommit = entry.autoCommit)
                    original != null -> WordSuggestionCandidate(
                        text = original.text,
                        secondaryText = original.secondaryText,
                        confidence = original.confidence,
                        isEligibleForAutoCommit = entry.autoCommit,
                        isEligibleForUserRemoval = original.isEligibleForUserRemoval,
                        sourceProvider = original.sourceProvider,
                        isLearned = original.isLearned,
                    )
                    else -> WordSuggestionCandidate(
                        text = entry.text,
                        confidence = 1.0,
                        isEligibleForAutoCommit = entry.autoCommit,
                        // A long-press on one of these adds it to My words (already there: says so).
                        isEligibleForUserRemoval = false,
                        sourceProvider = null,
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // A newer keystroke replaced this request; stop instead of finishing stale work.
            throw e
        } catch (e: Exception) {
            flogError { "TypeBetter: rules failed for a suggestion, keeping the keyboard's own: $e" }
            stock
        }
    }

    /**
     * The automatic My fix for [typed] as a candidate Space may commit, or null (see
     * [com.typebetter.personal.TypingRules.autoFixFor]). Cheap and synchronous: safe on the typing path.
     * Never throws.
     */
    fun personalFixNow(typed: String, autoCorrect: Boolean): WordSuggestionCandidate? {
        if (typed.isBlank()) return null
        val meant = try {
            // Rules only: a fix never depends on My words, and building them can read the database.
            com.typebetter.personal.TypingRules(PersonalWords.rules.value, emptyList()).autoFixFor(typed, autoCorrect)
        } catch (e: Exception) {
            flogError { "TypeBetter: rules failed for a word, keeping it as typed: $e" }
            null
        } ?: return null
        return WordSuggestionCandidate(
            text = meant,
            confidence = 1.0,
            isEligibleForAutoCommit = true,
            isEligibleForUserRemoval = false,
            sourceProvider = null,
        )
    }

    /** Word slots the classic strip shows; the offers go at its end so they stay visible there. */
    private const val VISIBLE_SLOTS = 3

    /**
     * Puts the pending one-tap offers among [candidates], in the last visible slot. Offers end as soon as the next word is
     * started or another field is opened, and are never shown in a password or incognito field. While the
     * offered word itself is still the composing text (the moment between finishing it and the space
     * landing), the offer stays.
     */
    fun withOffers(
        candidates: List<SuggestionCandidate>,
        composingText: String,
        isPrivateField: Boolean,
        context: Context,
    ): List<SuggestionCandidate> {
        val offers = PersonalWords.offers.value
        if (offers.isEmpty()) return candidates
        val composing = composingText.trim()
        val stillOnWord = composing.isEmpty() || offers.all { keyOf(it.word) == keyOf(composing) }
        if (!stillOnWord || isPrivateField) {
            PersonalWords.dropStaleOffers(offers)
            return candidates
        }
        val offered = offers.filterIsInstance<StripOffer.AddWord>().map { it.word }.toSet()
        val kept = candidates.filterNot { it.text.toString() in offered }
        // The offers take the last of the three visible slots, not the first: a finger already on its way
        // to the first suggestion when an offer arrives must still get that suggestion, not "+ word".
        val before = (VISIBLE_SLOTS - offers.size).coerceAtLeast(0)
        return kept.take(before) + offers.map { PersonalOfferCandidate(it, context) } + kept.drop(before)
    }
}
