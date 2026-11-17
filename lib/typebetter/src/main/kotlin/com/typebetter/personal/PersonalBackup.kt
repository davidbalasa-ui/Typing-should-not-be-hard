package com.typebetter.personal

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reading and writing TypeBetter's data as JSON: the rules file the keyboard keeps, and the single backup
 * file the user can export and import (rules plus My words).
 *
 * Unknown fields are ignored on read, so a file written by a newer version still opens in an older one.
 * An option this version does not know (a new fix mode or source) is read as the safest known one: an
 * unknown mode as [FixMode.SUGGEST], so it never swaps words by itself; an unknown source as
 * [Source.IMPORTED].
 */
object PersonalCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encodeRules(rules: PersonalRules): String = json.encodeToString(PersonalRules.serializer(), rules)

    /**
     * Deepest nesting a real file has is 3 (backup, list, entry). The JSON reader is recursive, so a
     * small file nested a few thousand levels deep would overflow the stack and crash the app; such a
     * file is refused before it is parsed.
     */
    private const val MAX_DEPTH = 32

    /** True when [text] nests arrays or objects deeper than [MAX_DEPTH] (brackets inside strings don't count). */
    internal fun nestedTooDeep(text: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[', '{' -> if (++depth > MAX_DEPTH) return true
                ']', '}' -> depth--
            }
        }
        return false
    }

    /** Returns null when [text] is not a rules file, so a damaged file can fall back to its copy. */
    fun decodeRules(text: String): PersonalRules? = if (nestedTooDeep(text)) null else try {
        json.decodeFromJsonElement(PersonalRules.serializer(), withKnownOptions(json.parseToJsonElement(clean(text))))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun encodeBackup(backup: PersonalBackup): String = json.encodeToString(PersonalBackup.serializer(), backup)

    /**
     * Reads an exported backup, or a saved rules file (the keyboard's own `personal-rules.json` or an old
     * weekly copy), which carries the fixes and Never change entries but no words.
     * Returns null when [text] is neither.
     */
    fun decodeBackup(text: String): PersonalBackup? {
        // The format field has a default, so a file must actually say it is a backup.
        if (nestedTooDeep(text)) return null
        val backup = if (!text.contains(PersonalBackup.FORMAT)) null else try {
            json.decodeFromJsonElement(PersonalBackup.serializer(), withKnownOptions(json.parseToJsonElement(clean(text))))
                .takeIf { it.format == PersonalBackup.FORMAT }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (backup != null) return backup
        if (!text.contains("\"fixes\"") && !text.contains("\"neverChange\"")) return null
        val rules = decodeRules(text) ?: return null
        if (rules.fixes.isEmpty() && rules.neverChange.isEmpty()) return null
        return PersonalBackup.of(rules, emptyList(), 0L)
    }

    /** A file saved by Windows Notepad starts with a byte-order mark, which is not JSON. */
    private fun clean(text: String): String = text.removePrefix("\uFEFF")

    private val knownModes = FixMode.entries.map { it.name }.toSet()
    private val knownSources = Source.entries.map { it.name }.toSet()

    /** Replaces option values this version does not know (see the class comment) before decoding. */
    private fun withKnownOptions(root: JsonElement): JsonElement {
        if (root !is JsonObject) return root
        fun JsonElement.entries(fix: (JsonObject) -> JsonObject): JsonElement =
            if (this is JsonArray) JsonArray(map { if (it is JsonObject) fix(it) else it }) else this
        fun JsonObject.known(field: String, known: Set<String>, fallback: Enum<*>): JsonObject {
            val value = (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return this
            return if (value in known) this else JsonObject(this + (field to JsonPrimitive(fallback.name)))
        }
        val fixed = root.toMutableMap()
        root["fixes"]?.let { list ->
            fixed["fixes"] = list.entries { it.known("mode", knownModes, FixMode.SUGGEST).known("source", knownSources, Source.IMPORTED) }
        }
        root["neverChange"]?.let { list ->
            fixed["neverChange"] = list.entries { it.known("source", knownSources, Source.IMPORTED) }
        }
        return JsonObject(fixed)
    }
}

/** The file "Export my words" writes and "Import" reads. */
@Serializable
data class PersonalBackup(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val words: List<String> = emptyList(),
    val fixes: List<Fix> = emptyList(),
    val neverChange: List<NeverChange> = emptyList(),
    /**
     * The starter pack's state when the file was written: the version applied and the words it added.
     * Null in files from before this was saved. Lets a restore on a new phone keep starter fixes the user
     * had deleted deleted, and lets "switch the pack off" still find the pack's words afterwards.
     */
    val starterPackVersion: Int? = null,
    val starterWords: List<String>? = null,
) {
    companion object {
        const val FORMAT = "typebetter-personal-words"

        fun of(rules: PersonalRules, words: List<String>, now: Long) = PersonalBackup(
            exportedAt = now,
            words = words.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            fixes = rules.fixes,
            neverChange = rules.neverChange,
            starterPackVersion = rules.starterPackVersion,
            starterWords = rules.starterWords,
        )
    }

    /**
     * This copy plus whatever [earlier] (the same week's copy as saved before) held that this one no longer
     * does: words, fixes and Never change entries deleted since. A weekly copy is only ever restored by
     * adding, so keeping them costs nothing, and a word deleted this week (even one added this week) or a
     * bulk mistake can still be brought back from this week's copy. Starter fixes are not kept: switching
     * the pack back on brings those.
     */
    fun keepingEarlier(earlier: PersonalBackup?): PersonalBackup {
        if (earlier == null) return this
        val wordKeys = words.map { keyOf(it) }.toHashSet()
        val fixKeys = fixes.map { it.key }.toHashSet()
        val blockKeys = neverChange.map { it.key }.toHashSet()
        return copy(
            words = words + earlier.words.filter { wordKeys.add(keyOf(it)) },
            fixes = fixes + earlier.fixes.filter { it.source != Source.STARTER && fixKeys.add(it.key) },
            neverChange = neverChange + earlier.neverChange.filter { blockKeys.add(it.key) },
        )
    }
}

/**
 * What an import changes. Importing only ever adds: an entry the user already has stays as it is, so
 * importing an old backup can never undo newer work.
 */
data class ImportResult(
    val rules: PersonalRules,
    val newWords: List<String>,
    val addedFixes: Int,
    val addedBlocks: Int,
    /** Starter fixes taken out because the backup's owner had deleted them (new phone only, see [PersonalImport]). */
    val removedStarterFixes: List<Fix> = emptyList(),
    /**
     * Starter words to take back out of the personal dictionary, for the same reason (new phone only). The
     * caller deletes them; they are already gone from [PersonalRules.starterWords].
     */
    val removedStarterWords: List<String> = emptyList(),
)

object PersonalImport {
    fun merge(current: PersonalRules, currentWords: Collection<String>, backup: PersonalBackup): ImportResult {
        val knownWords = currentWords.map { keyOf(it) }.toHashSet()
        val newWords = backup.words
            .map { it.trim() }
            .filter { it.isNotEmpty() && knownWords.add(keyOf(it)) }

        var rules = current
        // Restoring onto a new phone: the pack was added automatically at first start, so starter fixes the
        // backup's owner had deleted came back. The backup knows which ones they kept, so the others go.
        // Only on a phone with nothing of the user's own yet (every fix from the pack, no Never change), and
        // only for a backup made with this pack version or newer, which knows every entry the pack has. On
        // a phone in use, an import only ever adds.
        var removed = emptyList<Fix>()
        var removedWords = emptyList<String>()
        val freshPhone = rules.neverChange.isEmpty() && rules.fixes.all { it.source == Source.STARTER }
        if (freshPhone && backup.starterPackVersion != null && backup.starterPackVersion >= StarterPack.VERSION) {
            val keptStarter = backup.fixes.filter { it.source == Source.STARTER }.map { it.key }.toSet()
            removed = rules.fixes.filter { it.source == Source.STARTER && it.key !in keptStarter }
            rules = rules.copy(fixes = rules.fixes - removed.toSet())
            // The same for the pack's words: one the backup's owner deleted is in neither its words nor its
            // record of the pack's words. Only when the backup carries that record.
            val ownerWords = backup.starterWords
            if (ownerWords != null) {
                val stillThere = (ownerWords + backup.words).map { keyOf(it) }.toHashSet()
                removedWords = rules.starterWords.filter { keyOf(it) !in stillThere }
                if (removedWords.isNotEmpty()) {
                    val gone = removedWords.map { keyOf(it) }.toHashSet()
                    rules = rules.copy(starterWords = rules.starterWords.filterNot { keyOf(it) in gone })
                }
            }
        }
        var fixes = 0
        for (fix in backup.fixes) {
            // The same check the settings dialog uses: a file cannot add a fix the user could not type in
            // (blank, changing nothing, line breaks, or far too long). Skipped, never a failed import.
            if (!isUsableFix(fix.typed, fix.meant) || rules.fixFor(fix.typed) != null) continue
            // Never let an import overturn a "Never change" the user already set.
            if (rules.isBlocked(fix.typed, fix.meant)) continue
            rules = rules.withFix(fix) // keeps its source, so a restored starter entry can still be switched off
            fixes++
        }
        var blocks = 0
        for (block in backup.neverChange) {
            if (block.typed.isBlank() || block.blocked.isBlank() || rules.isBlocked(block.typed, block.blocked)) continue
            // …nor remove a fix the user already has.
            if (rules.fixFor(block.typed)?.let { keyOf(it.meant) == keyOf(block.blocked) } == true) continue
            rules = rules.withNeverChange(block)
            blocks++
        }
        // The pack's words that came back with this import stay the pack's, so switching it off finds them.
        // Only words that really are the pack's: a backup cannot make a word of the user's own removable
        // by "switch the pack off".
        val packKeys = StarterPack.words.map { keyOf(it) }.toHashSet()
        val packWords = backup.starterWords.orEmpty().filter { keyOf(it) in packKeys }
        if (packWords.isNotEmpty()) {
            rules = rules.copy(
                starterWords = (rules.starterWords + packWords).distinctBy { keyOf(it) },
                starterPackVersion = maxOf(rules.starterPackVersion, backup.starterPackVersion ?: 0),
            )
        }
        return ImportResult(rules, newWords, fixes, blocks, removed, removedWords)
    }
}
