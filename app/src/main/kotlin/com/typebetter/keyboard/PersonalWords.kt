package com.typebetter.keyboard

import android.content.Context
import android.net.Uri
import com.typebetter.personal.Fix
import com.typebetter.personal.NeverChange
import com.typebetter.personal.PersonalBackup
import com.typebetter.personal.PersonalCodec
import com.typebetter.personal.PersonalImport
import com.typebetter.personal.PersonalRules
import com.typebetter.personal.PromptVocabulary
import com.typebetter.personal.StarterPack
import com.typebetter.personal.StripOffer
import com.typebetter.personal.StripOffers
import com.typebetter.personal.TranscriptFixer
import com.typebetter.personal.TypingRules
import com.typebetter.personal.keyOf
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_MAX
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * TypeBetter's personal word lists, as the keyboard sees them.
 *
 * - **My words** are the keyboard's personal dictionary (the FlorisBoard user dictionary). That is where
 *   the prediction, glide and spell-check code already looks, so a word added here is known everywhere at
 *   once. Entries with a shortcut are text expansions, not words, and are left out.
 * - **My fixes** and **Never change** are [PersonalRules], saved as JSON in the app's private files:
 *   `typebetter/personal-rules.json`, with the previous version kept as `.bak` and one copy a week in
 *   `typebetter/backups/` (the last eight weeks).
 *
 * The rules themselves are plain Kotlin in `lib/typebetter`; this object only stores them, keeps them
 * in memory for the typing path, and tells the keyboard when they change ([changes]).
 */
object PersonalWords {
    private const val DIR = "typebetter"
    private const val RULES_FILE = "personal-rules.json"
    private const val BACKUP_DIR = "backups"
    private const val WEEKLY_BACKUPS_KEPT = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    @Volatile
    private var appContext: Context? = null

    private val _rules = MutableStateFlow(PersonalRules())
    val rules: StateFlow<PersonalRules> = _rules.asStateFlow()

    /** Bumped on every change to My words or the rules; the keyboard re-reads its suggestions on it. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** What the suggestion strip offers right now (see [StripOffer]); empty most of the time. */
    private val _offers = MutableStateFlow<List<StripOffer>>(emptyList())
    val offers: StateFlow<List<StripOffer>> = _offers.asStateFlow()

    // ---------------------------------------------------------------------------------------------
    // Start-up

    fun init(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        scope.launch {
            val loaded = loadRules(app)
            _rules.value = loaded
            applyStarterPack(force = false)
            weeklyBackup(app)
            bump()
        }
    }

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun loadRules(context: Context): PersonalRules {
        val file = File(dir(context), RULES_FILE)
        val backup = File(dir(context), "$RULES_FILE.bak")
        for (candidate in listOf(file, backup)) {
            if (!candidate.exists()) continue
            val text = runCatching { candidate.readText() }.getOrNull() ?: continue
            PersonalCodec.decodeRules(text)?.let { return it }
            flogError { "TypeBetter: ${candidate.name} is unreadable, trying the next copy" }
        }
        return PersonalRules()
    }

    /** Writes the rules atomically: new file beside the old, old kept as `.bak`, then a rename. */
    private suspend fun persist(rules: PersonalRules) = writeLock.withLock {
        val context = appContext ?: return@withLock
        runCatching {
            val folder = dir(context)
            val file = File(folder, RULES_FILE)
            val temp = File(folder, "$RULES_FILE.tmp")
            temp.writeText(PersonalCodec.encodeRules(rules))
            if (file.exists()) file.copyTo(File(folder, "$RULES_FILE.bak"), overwrite = true)
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }.onFailure { flogError { "TypeBetter: could not save the rules: $it" } }
    }

    private fun weeklyBackup(context: Context) {
        runCatching {
            val source = File(dir(context), RULES_FILE)
            if (!source.exists()) return
            val folder = File(dir(context), BACKUP_DIR).apply { mkdirs() }
            val today = LocalDate.now()
            val week = "%d-W%02d".format(today.get(IsoFields.WEEK_BASED_YEAR), today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
            val target = File(folder, "personal-rules-$week.json")
            if (!target.exists()) source.copyTo(target)
            folder.listFiles { f -> f.name.startsWith("personal-rules-") }
                ?.sortedByDescending { it.name }
                ?.drop(WEEKLY_BACKUPS_KEPT)
                ?.forEach { it.delete() }
        }.onFailure { flogError { "TypeBetter: weekly copy failed: $it" } }
    }

    private fun update(change: (PersonalRules) -> PersonalRules) {
        var updated: PersonalRules? = null
        _rules.update { current -> change(current).also { updated = it } }
        updated?.let { rules -> scope.launch { persist(rules) } }
        bump()
    }

    private fun bump() {
        _changes.update { it + 1 }
    }

    // ---------------------------------------------------------------------------------------------
    // My words (the personal dictionary)

    @Volatile
    private var cachedWords: List<String> = emptyList()

    @Volatile
    private var cachedWordsVersion = Int.MIN_VALUE

    /** My words, read from the personal dictionary and cached until it changes. */
    fun myWords(): List<String> {
        val manager = runCatching { DictionaryManager.default() }.getOrNull() ?: return emptyList()
        manager.loadUserDictionariesIfNecessary()
        val version = manager.userVocabularyVersion
        if (version != cachedWordsVersion) {
            cachedWords = runCatching {
                manager.florisUserDictionaryDao()?.queryAll()
                    ?.filter { it.shortcut.isNullOrBlank() }
                    ?.map { it.word.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
            }.getOrNull() ?: emptyList()
            cachedWordsVersion = version
        }
        return cachedWords
    }

    fun isMyWord(word: String): Boolean {
        val key = keyOf(word)
        return myWords().any { keyOf(it) == key }
    }

    /**
     * Adds [words] to My words for every language. Returns the ones that were new.
     * Runs on the caller's thread; the dictionary allows that, and every caller is off the main thread
     * or adds one word.
     */
    fun addWords(words: List<String>): List<String> {
        val manager = runCatching { DictionaryManager.default() }.getOrNull() ?: return emptyList()
        manager.loadUserDictionariesIfNecessary()
        val dao = manager.florisUserDictionaryDao() ?: return emptyList()
        val known = myWords().map { keyOf(it) }.toHashSet()
        val added = mutableListOf<String>()
        for (raw in words) {
            val word = raw.trim()
            if (word.isEmpty() || !known.add(keyOf(word))) continue
            runCatching {
                dao.insert(UserDictionaryEntry(id = 0, word = word, freq = FREQUENCY_MAX, locale = null, shortcut = null))
            }.onSuccess { added += word }
        }
        if (added.isNotEmpty()) {
            cachedWordsVersion = Int.MIN_VALUE
            bump()
        }
        return added
    }

    fun addWord(word: String): Boolean = addWords(listOf(word)).isNotEmpty()

    /** Removes [words] from My words, whatever their case. */
    fun removeWords(words: List<String>) {
        if (words.isEmpty()) return
        val manager = runCatching { DictionaryManager.default() }.getOrNull() ?: return
        manager.loadUserDictionariesIfNecessary()
        val dao = manager.florisUserDictionaryDao() ?: return
        val keys = words.map { keyOf(it) }.toHashSet()
        runCatching {
            dao.queryAll().filter { it.shortcut.isNullOrBlank() && keyOf(it.word) in keys }.forEach { dao.delete(it) }
        }
        cachedWordsVersion = Int.MIN_VALUE
        bump()
    }

    // ---------------------------------------------------------------------------------------------
    // My fixes and Never change

    fun setFix(fix: Fix, replacing: String? = null) = update { rules ->
        val base = if (replacing != null && keyOf(replacing) != fix.key) rules.withoutFix(replacing) else rules
        base.withFix(fix.copy(addedAt = if (fix.addedAt == 0L) System.currentTimeMillis() else fix.addedAt))
    }

    fun removeFix(typed: String) = update { it.withoutFix(typed) }

    fun setNeverChange(entry: NeverChange) = update { rules ->
        rules.withNeverChange(entry.copy(addedAt = if (entry.addedAt == 0L) System.currentTimeMillis() else entry.addedAt))
    }

    fun removeNeverChange(typed: String, blocked: String) = update { it.withoutNeverChange(typed, blocked) }

    // ---------------------------------------------------------------------------------------------
    // Typing and dictation

    @Volatile
    private var cachedTypingRules: Triple<PersonalRules, List<String>, TypingRules>? = null

    /** The typing rules for the current lists, rebuilt only when a list changed. */
    fun typingRules(): TypingRules {
        val rules = _rules.value
        val words = myWords()
        cachedTypingRules?.let { (r, w, t) -> if (r === rules && w === words) return t }
        return TypingRules(rules, words).also { cachedTypingRules = Triple(rules, words, it) }
    }

    @Volatile
    private var cachedFixer: Pair<PersonalRules, TranscriptFixer>? = null

    /** My fixes applied to a dictated transcript (automatic fixes only, phrases included). */
    fun fixTranscript(text: String): String {
        val rules = _rules.value
        val fixer = cachedFixer?.takeIf { it.first === rules }?.second
            ?: TranscriptFixer(rules).also { cachedFixer = rules to it }
        return runCatching { fixer.apply(text) }.getOrDefault(text)
    }

    /**
     * The words to give the speech model: the user's own custom words first and untouched, then the
     * fix targets and My words that fit in [tokenBudget].
     */
    fun promptVocabulary(customWords: List<String>, tokenBudget: Int): List<String> =
        runCatching { PromptVocabulary.build(customWords, _rules.value, myWords(), tokenBudget) }
            .getOrDefault(customWords)

    // ---------------------------------------------------------------------------------------------
    // One-tap offers in the strip

    /** Backspace just undid an automatic correction from [typed] to [corrected]. */
    fun onCorrectionUndone(typed: String, corrected: String) {
        var offers: List<StripOffer> = emptyList()
        update { rules ->
            val (updated, shown) = StripOffers.afterUndo(rules, typed, corrected, isMyWord(typed))
            offers = shown
            updated
        }
        _offers.value = offers
    }

    /** A word was finished without a correction. [isKnown] says whether the keyboard's dictionary has it. */
    fun onWordFinished(word: String, isKnown: Boolean) {
        val offer = StripOffers.afterWord(word, isKnown, isMyWord(word))
        _offers.value = listOfNotNull(offer)
    }

    fun clearOffers() {
        if (_offers.value.isNotEmpty()) _offers.value = emptyList()
    }

    /** The user tapped an offer. */
    fun accept(offer: StripOffer) {
        clearOffers()
        when (offer) {
            is StripOffer.AddWord -> scope.launch { addWord(offer.word) }
            is StripOffer.StopChanging -> setNeverChange(NeverChange(offer.typed, offer.corrected))
        }
    }

    /** The user dismissed the question without answering; it is not asked again for this pair. */
    fun decline(offer: StripOffer) {
        clearOffers()
        if (offer is StripOffer.StopChanging) update { it.markAsked(offer.typed, offer.corrected) }
    }

    // ---------------------------------------------------------------------------------------------
    // Starter pack

    fun isStarterPackOn(): Boolean = StarterPack.isActive(_rules.value)

    fun setStarterPack(on: Boolean) {
        scope.launch {
            if (on) applyStarterPack(force = true) else removeStarterPack()
        }
    }

    private fun applyStarterPack(force: Boolean) {
        val (withPack, words) = StarterPack.apply(_rules.value, force)
        if (withPack === _rules.value && words.isEmpty()) return
        val added = addWords(words)
        // Re-applied to the current rules, so a change made meanwhile is not lost.
        update { current -> StarterPack.recordWordsAdded(StarterPack.apply(current, force = true).first, added) }
    }

    private fun removeStarterPack() {
        var words: List<String> = emptyList()
        update { rules -> StarterPack.remove(rules).also { words = it.second }.first }
        removeWords(words)
    }

    // ---------------------------------------------------------------------------------------------
    // Export and import

    data class ImportSummary(val words: Int, val fixes: Int, val blocks: Int)

    fun exportTo(context: Context, uri: Uri): Boolean = runCatching {
        val backup = PersonalBackup.of(_rules.value, myWords(), System.currentTimeMillis())
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(PersonalCodec.encodeBackup(backup).toByteArray(Charsets.UTF_8))
        } ?: error("no output stream")
        true
    }.getOrElse {
        flogError { "TypeBetter: export failed: $it" }
        false
    }

    /** Returns null when the file is not a TypeBetter backup. Importing only adds. */
    fun importFrom(context: Context, uri: Uri): ImportSummary? {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return null
        val backup = PersonalCodec.decodeBackup(text) ?: return null
        val merged = PersonalImport.merge(_rules.value, myWords(), backup)
        update { current -> PersonalImport.merge(current, emptyList(), backup).rules }
        val added = addWords(merged.newWords)
        return ImportSummary(added.size, merged.addedFixes, merged.addedBlocks)
    }
}
