package com.typebetter.keyboard

import android.content.Context
import com.typebetter.personal.StripOffer
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
        isDictionaryWord: suspend (String) -> Boolean,
    ): List<SuggestionCandidate> {
        if (typed.isBlank()) return stock
        return try {
            val key = keyOf(typed)
            // Asked once, here, because the rules run synchronously and only ever ask about the typed word.
            val typedIsCommon = runCatching { isDictionaryWord(key) }.getOrDefault(true)
            val entries = PersonalWords.typingRules().apply(
                typed = typed,
                stock = stock,
                textOf = { it.text.toString() },
                autoCommitOf = { it.isEligibleForAutoCommit },
                isCommonWord = { it == key && typedIsCommon },
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
        } catch (e: Exception) {
            flogError { "TypeBetter: rules failed for a suggestion, keeping the keyboard's own: $e" }
            stock
        }
    }

    /**
     * Puts the pending one-tap offers in front of [candidates]. Offers end as soon as the next word is
     * started, and are never shown in a password field.
     */
    fun withOffers(
        candidates: List<SuggestionCandidate>,
        isComposing: Boolean,
        isPassword: Boolean,
        context: Context,
    ): List<SuggestionCandidate> {
        val offers = PersonalWords.offers.value
        if (offers.isEmpty()) return candidates
        if (isComposing || isPassword) {
            PersonalWords.clearOffers()
            return candidates
        }
        val offered = offers.filterIsInstance<StripOffer.AddWord>().map { it.word }.toSet()
        return offers.map { PersonalOfferCandidate(it, context) } +
            candidates.filterNot { it.text.toString() in offered }
    }
}
