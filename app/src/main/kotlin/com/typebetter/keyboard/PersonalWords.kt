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
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private const val WEEKLY_PREFIX = "typebetter-week-"
    private const val UNREADABLE = ".unreadable-"
    private const val UNREADABLE_KEPT = 3
    private const val MAX_WORD_LENGTH = 48

    /** Far larger than any real backup (a few hundred kilobytes for thousands of entries). */
    private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

    // A failure in background work (a dictionary that will not open, a full disk) is logged, never a crash.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> flogError { "TypeBetter: $e" } },
    )
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

    /**
     * Ticket for the newest offer request. Offers are worked out off the main thread, so an answer can
     * arrive after the user has moved on: to another field (it would show there) or to a later word whose
     * answer came back first. Only the newest request may set offers, and a clear cancels every request
     * still in flight.
     */
    private var offerTicket = 0L
    private val offerLock = Any()

    private fun newOfferTicket(): Long = synchronized(offerLock) { ++offerTicket }

    private fun setOffers(ticket: Long, offers: List<StripOffer>) {
        synchronized(offerLock) {
            if (ticket == offerTicket) _offers.value = offers
        }
    }
    val offers: StateFlow<List<StripOffer>> = _offers.asStateFlow()

    // ---------------------------------------------------------------------------------------------
    // Start-up

    /** Guards [ready] and [pending]: changes made before the saved rules are loaded wait for them. */
    private val loadLock = Any()

    @Volatile
    private var ready = false
    private val pending = mutableListOf<(PersonalRules) -> PersonalRules>()

    /**
     * False until the keyboard's settings have loaded. Before that, whether the personal dictionary is
     * switched on is not known yet, so nothing reads or writes it (no starter words, no weekly copy).
     */
    @Volatile
    private var settingsLoaded = false

    /**
     * True when the saved rules exist but could not be read (storage locked or failing), and could not be
     * kept aside either. Saving is then refused for this run, so an empty list never overwrites them.
     */
    @Volatile
    private var saveBlocked = false

    fun init(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        scope.launch {
            val loaded = loadRules(app)
            synchronized(loadLock) {
                // Anything changed while the file was loading is replayed on top of it, not lost.
                var rules = loaded
                pending.forEach { change -> rules = runCatching { change(rules) }.getOrDefault(rules) }
                pending.clear()
                _rules.value = rules
                ready = true
            }
            // The settings load in the background at start-up; the dictionary switches live there.
            (app as? FlorisApplication)?.preferenceStoreLoaded?.first { it }
            settingsLoaded = true
            persist()
            applyStarterPack(force = false)
            bump()
        }
    }

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun loadRules(context: Context): PersonalRules {
        val file = File(dir(context), RULES_FILE)
        val backup = File(dir(context), "$RULES_FILE.bak")
        var unsafe = false
        for (candidate in listOf(file, backup)) {
            if (!candidate.exists()) continue
            val text = runCatching { candidate.readText() }.getOrNull()
            text?.let { PersonalCodec.decodeRules(it) }?.let { return it }
            // Kept aside rather than overwritten, so a bad read never costs the saved lists.
            flogError { "TypeBetter: ${candidate.name} is unreadable, keeping it aside and trying the next copy" }
            val keptAside = runCatching {
                candidate.copyTo(File(candidate.parentFile, "${candidate.name}$UNREADABLE${System.currentTimeMillis()}"))
            }.isSuccess
            // A file that could not even be read (rather than read and found damaged) may be fine once the
            // storage recovers; if it could not be kept aside either, it must not be overwritten.
            if (text == null && !keptAside) unsafe = true
        }
        saveBlocked = unsafe
        pruneUnreadableCopies(context)
        return PersonalRules()
    }

    /** Keeps only the newest few damaged copies, so repeated failures cannot fill the storage. */
    private fun pruneUnreadableCopies(context: Context) {
        runCatching {
            dir(context).listFiles { f -> UNREADABLE in f.name }
                ?.sortedByDescending { it.name.substringAfterLast(UNREADABLE).toLongOrNull() ?: 0L }
                ?.drop(UNREADABLE_KEPT)
                ?.forEach { it.delete() }
        }
    }

    /**
     * Writes the current rules atomically: new file beside the old, old kept as `.bak`, then a rename.
     * Always the latest state, read inside the lock, so writes landing out of order cannot save an older one.
     */
    private suspend fun persist() = writeLock.withLock {
        val context = appContext ?: return@withLock
        if (!ready) return@withLock
        if (saveBlocked) {
            flogError { "TypeBetter: the saved rules could not be read this run; not saving over them" }
            return@withLock
        }
        val rules = _rules.value
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
        weeklyBackup(context)
    }

    /**
     * One full copy a week (My words, My fixes and Never change, in the export format), the last eight
     * kept; each holds the last state of its week. Settings → My words & fixes → Restore a weekly copy brings one back.
     *
     * Called with [writeLock] held, after every change to the rules and to My words. Written to a temporary
     * file and renamed, so a crash mid-write leaves the previous copy whole. While My words cannot be read
     * (dictionary off or failing), the copy keeps the words it already holds and only the fixes are
     * updated: a copy without the words must never replace a good one.
     */
    private fun weeklyBackup(context: Context) {
        runCatching {
            if (!ready || !settingsLoaded || saveBlocked) return
            val folder = File(dir(context), BACKUP_DIR).apply { mkdirs() }
            val today = LocalDate.now()
            val week = "%d-W%02d".format(today.get(IsoFields.WEEK_BASED_YEAR), today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
            val target = File(folder, "$WEEKLY_PREFIX$week.json")
            val words = myWordsOrNull()
                ?: lastSavedWords(target)
                ?: weeklyFiles(context).firstNotNullOfOrNull { lastSavedWords(it) }
                ?: return
            // Rewritten on every save during the week, so the copy holds the week's latest state.
            val backup = PersonalBackup.of(_rules.value, words, System.currentTimeMillis())
            if (backup.words.isEmpty() && backup.fixes.isEmpty() && backup.neverChange.isEmpty()) return
            val temp = File(folder, "${target.name}.tmp")
            temp.writeText(PersonalCodec.encodeBackup(backup))
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            weeklyFiles(context).drop(WEEKLY_BACKUPS_KEPT).forEach { it.delete() }
        }.onFailure { flogError { "TypeBetter: weekly copy failed: $it" } }
    }

    /** The words a weekly copy holds, or null when it cannot be read. */
    private fun lastSavedWords(file: File): List<String>? =
        if (!file.exists()) null else runCatching { PersonalCodec.decodeBackup(file.readText())?.words }.getOrNull()

    /** Refreshes the weekly copy after My words changed (the rules' own saves refresh it too). */
    private fun refreshWeeklyCopy() {
        val context = appContext ?: return
        scope.launch { writeLock.withLock { weeklyBackup(context) } }
    }

    private fun weeklyFiles(context: Context): List<File> =
        File(dir(context), BACKUP_DIR).listFiles { f -> f.name.startsWith(WEEKLY_PREFIX) && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            .orEmpty()

    /** A weekly copy, for the restore list. [week] reads like "2026-W46". */
    data class WeeklyCopy(val week: String, val savedAt: Long, internal val file: File)

    fun weeklyCopies(): List<WeeklyCopy> {
        val context = appContext ?: return emptyList()
        return weeklyFiles(context).map {
            WeeklyCopy(it.name.removePrefix(WEEKLY_PREFIX).removeSuffix(".json"), it.lastModified(), it)
        }
    }

    /** Adds back what a weekly copy holds. Like every import it only adds; nothing is removed. */
    fun restoreWeekly(copy: WeeklyCopy): ImportSummary? =
        runCatching { copy.file.readText() }.getOrNull()?.let { importText(it) }

    private fun update(change: (PersonalRules) -> PersonalRules) {
        var before: PersonalRules? = null
        var after: PersonalRules? = null
        // One lock around both steps: otherwise loading could finish in between, replay the queued change
        // onto the loaded rules, and this would then apply it a second time on top.
        try {
            synchronized(loadLock) {
                _rules.update { current -> change(current).also { before = current; after = it } }
                // Queued only once it has applied cleanly, so a refused change is not replayed at load.
                if (!ready) pending += change
            }
        } catch (e: IllegalArgumentException) {
            // A change the rules refuse (an empty or no-op fix) changes nothing; it must not crash the
            // settings screen or the keyboard that asked for it.
            flogError { "TypeBetter: change refused: ${e.message}" }
            return
        }
        scope.launch { persist() }
        // Undo counters do not change what the keyboard suggests; only the lists do.
        val b = before
        val a = after
        if (b == null || a == null || b.fixes != a.fixes || b.neverChange != a.neverChange) bump()
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

    /**
     * My words, read from the personal dictionary and cached until it changes. While the dictionary cannot
     * be read, the last list read is returned (empty before the first read).
     */
    fun myWords(): List<String> = myWordsOrNull() ?: cachedWords

    /** The last My words list read, without touching the dictionary (safe on the main thread). */
    fun myWordsCached(): List<String> = cachedWords

    /** My words, or null when the personal dictionary cannot be read right now (off, not loaded, failing). */
    private fun myWordsOrNull(): List<String>? {
        if (!settingsLoaded) return null
        val manager = runCatching { DictionaryManager.default() }.getOrNull() ?: return null
        manager.loadUserDictionariesIfNecessary()
        val version = manager.userVocabularyVersion
        if (version != cachedWordsVersion) {
            val read = runCatching {
                manager.florisUserDictionaryDao()?.queryAll()
                    ?.filter { it.shortcut.isNullOrBlank() }
                    ?.map { it.word.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
            }.getOrNull() ?: return null
            // Only a successful read is cached, so one failure is not remembered as "no words".
            cachedWords = read
            cachedWordsVersion = version
        }
        return cachedWords
    }

    /** Forgets the cached list, so the next read sees a change just written. */
    private fun invalidateWords() {
        cachedWordsVersion = Int.MIN_VALUE
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
        invalidateWords()
        val known = (myWordsOrNull() ?: return emptyList()).map { keyOf(it) }.toHashSet()
        val added = mutableListOf<String>()
        val insertAll = {
            for (raw in words) {
                val word = raw.trim()
                // A word is one word: an entry with spaces would never match anything typed.
                if (!isValidWord(word) || !known.add(keyOf(word))) continue
                runCatching {
                    dao.insert(UserDictionaryEntry(id = 0, word = word, freq = FREQUENCY_MAX, locale = null, shortcut = null))
                }.onSuccess { added += word }
            }
        }
        // Many words at once (starter pack, Learn from text, import) go in as one transaction, so the
        // keyboard sees one dictionary change and rebuilds its word caches once instead of once per word.
        val database = if (words.size > 1) manager.florisUserDictionaryDatabase() else null
        if (database != null) {
            runCatching { database.runInTransaction(Runnable { insertAll() }) }.onFailure {
                // A failed transaction rolls back every insert; report nothing added and keep the cache honest.
                flogError { "TypeBetter: adding ${words.size} words failed: $it" }
                added.clear()
            }
        } else {
            insertAll()
        }
        if (added.isNotEmpty()) {
            invalidateWords()
            bump()
            refreshWeeklyCopy()
        }
        return added
    }

    /** What My words accepts: one word, no spaces, at most [MAX_WORD_LENGTH] characters. */
    fun isValidWord(word: String): Boolean =
        word.isNotBlank() && word.none { it.isWhitespace() } && word.length <= MAX_WORD_LENGTH

    /**
     * Changes [old] to [new] in My words. The new spelling is written before the old one is removed, so a
     * failed write never loses the word. Returns false (and changes nothing) when it could not be saved.
     */
    fun renameWord(old: String, new: String): Boolean {
        val word = new.trim()
        if (!isValidWord(word)) return false
        if (old == word) return true
        if (keyOf(old) == keyOf(word)) {
            // Only the capitals change: the dictionary holds one entry per word, so swap it.
            removeWords(listOf(old))
            if (addWord(word)) return true
            addWord(old)
            return false
        }
        if (!addWord(word)) return false
        removeWords(listOf(old))
        return true
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
        invalidateWords()
        bump()
        refreshWeeklyCopy()
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

    /**
     * Backspace just undid an automatic correction from [typed] to [corrected]. With [remember] off (the
     * keyboard's word learning is off) the undo is not counted or saved; only the "+ word" offer shows.
     */
    fun onCorrectionUndone(typed: String, corrected: String, remember: Boolean) {
        val ticket = newOfferTicket()
        scope.launch {
            val mine = isMyWord(typed)
            if (!remember) {
                setOffers(
                    ticket,
                    StripOffers.afterUndo(_rules.value, typed, corrected, mine).second
                        .filterIsInstance<StripOffer.AddWord>(),
                )
                return@launch
            }
            var offers: List<StripOffer> = emptyList()
            update { rules ->
                val (updated, shown) = StripOffers.afterUndo(rules, typed, corrected, mine)
                offers = shown
                updated
            }
            setOffers(ticket, offers)
        }
    }

    /**
     * A word was finished without a correction. [isKnown] asks the keyboard's dictionary; it is called
     * off the main thread, like the personal dictionary lookup.
     */
    fun onWordFinished(word: String, isKnown: (String) -> Boolean) {
        val ticket = newOfferTicket()
        scope.launch {
            if (!StripOffers.looksAddable(word.trim())) return@launch
            val known = runCatching { isKnown(word) }.getOrDefault(true)
            val offer = StripOffers.afterWord(word, known, isMyWord(word))
            setOffers(ticket, listOfNotNull(offer))
        }
    }

    /**
     * Hides [shown] if those are still the offers on the strip, without cancelling an answer still being
     * worked out: the strip calls this when the user has moved past an old offer, which can happen just
     * after the next word was finished and its own offer requested.
     */
    fun dropStaleOffers(shown: List<StripOffer>) {
        synchronized(offerLock) {
            if (_offers.value === shown) _offers.value = emptyList()
        }
    }

    /** Ends every offer, shown or still being worked out (a new field, or an offer was tapped). */
    fun clearOffers() {
        synchronized(offerLock) {
            offerTicket++
            if (_offers.value.isNotEmpty()) _offers.value = emptyList()
        }
    }

    /** The user tapped an offer. */
    fun accept(offer: StripOffer) {
        clearOffers()
        when (offer) {
            is StripOffer.AddWord -> scope.launch { addWord(offer.word) }
            is StripOffer.StopChanging -> setNeverChange(NeverChange(offer.typed, offer.corrected))
        }
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
        // With the personal dictionary unavailable the words could not be added; try again next start
        // instead of marking the pack as applied without them.
        val manager = runCatching { DictionaryManager.default() }.getOrNull() ?: return
        manager.loadUserDictionariesIfNecessary()
        if (manager.florisUserDictionaryDao() == null) return
        val (withPack, words) = StarterPack.apply(_rules.value, force)
        if (withPack === _rules.value && words.isEmpty()) return
        val added = addWords(words)
        // Re-applied to the current rules, so a change made meanwhile is not lost. Same [force] as above:
        // an upgrade from an older pack version must not bring back entries the user deleted.
        update { current -> StarterPack.recordWordsAdded(StarterPack.apply(current, force).first, added) }
    }

    private fun removeStarterPack() {
        var words: List<String> = emptyList()
        update { rules -> StarterPack.remove(rules).also { words = it.second }.first }
        removeWords(words)
    }

    // ---------------------------------------------------------------------------------------------
    // Export and import

    /** What an import did. [wordsNotAdded]: words it holds that could not be added (dictionary off or failing). */
    data class ImportSummary(val words: Int, val fixes: Int, val blocks: Int, val wordsNotAdded: Int = 0)

    /** Everything, in the export format: what Export writes and the app's own Backup includes. */
    fun exportText(): String =
        PersonalCodec.encodeBackup(PersonalBackup.of(_rules.value, myWords(), System.currentTimeMillis()))

    fun exportTo(context: Context, uri: Uri): Boolean = runCatching {
        val text = exportText()
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("no output stream")
        true
    }.getOrElse {
        flogError { "TypeBetter: export failed: $it" }
        false
    }

    /**
     * What a file would add, shown to the user before anything changes. [autoFixes] lists the automatic
     * fixes in it, because those rewrite typing and dictation without asking.
     */
    data class ImportPreview(
        internal val backup: PersonalBackup,
        val words: Int,
        val fixes: Int,
        val blocks: Int,
        val autoFixes: List<Fix>,
        /** Starter fixes the backup's owner had deleted, taken out again (only on a phone not yet in use). */
        val removedStarter: List<Fix>,
    )

    /**
     * Reads a backup file and works out what importing it would add, without changing anything. Null
     * when the file is not a TypeBetter backup or is larger than any real one ([MAX_IMPORT_BYTES]).
     */
    fun previewImport(context: Context, uri: Uri): ImportPreview? {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // Read at most one byte past the limit, so a huge file is refused without loading it.
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (out.size() <= MAX_IMPORT_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                if (out.size() > MAX_IMPORT_BYTES) null else out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull() ?: return null
        val backup = PersonalCodec.decodeBackup(text) ?: return null
        return runCatching {
            val result = PersonalImport.merge(_rules.value, myWords(), backup)
            val before = _rules.value.fixes.map { it.key }.toSet()
            val newFixes = result.rules.fixes.filter { it.key !in before }
            ImportPreview(
                backup = backup,
                words = result.newWords.count { isValidWord(it) },
                fixes = result.addedFixes,
                blocks = result.addedBlocks,
                autoFixes = newFixes.filter { it.mode == com.typebetter.personal.FixMode.AUTO },
                removedStarter = result.removedStarterFixes,
            )
        }.getOrNull()
    }

    /** Applies a previewed import. Importing only adds (apart from starter entries, see [PersonalImport]). */
    fun applyImport(preview: ImportPreview): ImportSummary? = importBackup(preview.backup)

    /** Adds what [text] (an export, a weekly copy or a saved rules file) holds. Null when it is none of those. */
    fun importText(text: String): ImportSummary? {
        val backup = PersonalCodec.decodeBackup(text) ?: return null
        return importBackup(backup)
    }

    private fun importBackup(backup: PersonalBackup): ImportSummary? {
        return runCatching {
            var result: com.typebetter.personal.ImportResult? = null
            invalidateWords()
            // Fixes and Never change are restored even while My words cannot be read; the words that could
            // not be added are counted, so the user is told instead of them vanishing.
            val words = myWordsOrNull() ?: cachedWords
            update { current -> PersonalImport.merge(current, words, backup).also { result = it }.rules }
            val merged = result ?: return@runCatching null
            val added = addWords(merged.newWords)
            val wanted = merged.newWords.count { isValidWord(it) }
            ImportSummary(added.size, merged.addedFixes, merged.addedBlocks, wordsNotAdded = (wanted - added.size).coerceAtLeast(0))
        }.getOrElse {
            flogError { "TypeBetter: import failed: $it" }
            null
        }
    }
}
