package com.typebetter.keyboard

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.ui.graphics.vector.ImageVector
import com.typebetter.personal.StripOffer
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider

/**
 * A one-tap offer in the suggestion strip ("+ Kasmyra — Add to My words"). Tapping it does the action
 * instead of typing its text; it is never swapped in by Space and never added to the dictionary by a
 * long-press. See [StripOffer].
 */
class PersonalOfferCandidate(
    val offer: StripOffer,
    context: Context,
) : SuggestionCandidate {
    override val text: CharSequence = when (offer) {
        is StripOffer.AddWord -> offer.word
        is StripOffer.StopChanging -> context.getString(R.string.typebetter__offer_stop_title)
    }

    override val secondaryText: CharSequence = when (offer) {
        is StripOffer.AddWord -> context.getString(R.string.typebetter__offer_add_hint)
        is StripOffer.StopChanging -> "${offer.typed} → ${offer.corrected}"
    }

    override val confidence: Double = 1.0
    override val isEligibleForAutoCommit: Boolean = false
    override val isEligibleForUserRemoval: Boolean = false
    override val sourceProvider: SuggestionProvider? = null

    override val icon: ImageVector = when (offer) {
        is StripOffer.AddWord -> Icons.Default.Add
        is StripOffer.StopChanging -> Icons.Default.Block
    }

    fun accept() = PersonalWords.accept(offer)

    override fun equals(other: Any?): Boolean = other is PersonalOfferCandidate && other.offer == offer
    override fun hashCode(): Int = offer.hashCode()
}
