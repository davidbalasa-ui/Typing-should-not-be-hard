/*
 * TypeBetter: the dictionary look-up behind putting My words back into a dictation.
 */

package dev.patrickgold.florisboard.dictate

import android.content.Context
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import kotlin.math.roundToInt

/**
 * The English dictionary's frequency of a word (128..255), or null when it does not know the word; the
 * whole function is null when there is nothing to ask. [com.typebetter.personal.HeardNameFixer] uses it
 * to tell a real word from a misheard name, so its sound rules, which are English, only ever run on an
 * English dictation: when the keyboard's language is English and the dictation language is English or
 * left to the model ([DictateLanguages.DETECT]).
 *
 * Only if the keyboard is already up, as [transcriptTighteningSymbols] explains; a shared file or a watch
 * request without one gets My fixes alone.
 */
internal fun Context.englishWordFrequency(dictationLanguage: String): ((String) -> Int?)? = runCatching {
    val language = dictationLanguage.trim().lowercase()
    if (language.isNotEmpty() && language != DictateLanguages.DETECT && !language.startsWith("en")) return null
    val nlp = nlpManager()
    val subtypes = subtypeManager()
    if (!nlp.isInitialized() || !subtypes.isInitialized()) return null
    val subtype = subtypes.value.activeSubtype
    if (subtype.primaryLocale.language != "en") return null
    val manager = nlp.value
    val lookup: (String) -> Int? = { word ->
        val f = runCatching { manager.getFrequencyForWord(subtype, word) }.getOrDefault(0.0)
        if (f > 0.0) (f * 255).roundToInt() else null
    }
    // A dictionary that is not there answers 0 for everything, which would read every word as unknown
    // and so as safe to replace. "the" is in every English list: no answer for it, no fixing.
    if (lookup("the") == null) return null
    lookup
}.getOrNull()
