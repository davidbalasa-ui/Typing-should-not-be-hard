package com.typebetter.personal

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reading and writing TypeBetter's data as JSON: the rules file the keyboard keeps, and the single backup
 * file the user can export and import (rules plus My words).
 *
 * Unknown fields are ignored on read, so a file written by a newer version still opens in an older one.
 */
object PersonalCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encodeRules(rules: PersonalRules): String = json.encodeToString(PersonalRules.serializer(), rules)

    /** Returns null when [text] is not a rules file, so a damaged file can fall back to its copy. */
    fun decodeRules(text: String): PersonalRules? = try {
        json.decodeFromString(PersonalRules.serializer(), text)
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
        val backup = if (!text.contains(PersonalBackup.FORMAT)) null else try {
            json.decodeFromString(PersonalBackup.serializer(), text).takeIf { it.format == PersonalBackup.FORMAT }
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
) {
    companion object {
        const val FORMAT = "typebetter-personal-words"

        fun of(rules: PersonalRules, words: List<String>, now: Long) = PersonalBackup(
            exportedAt = now,
            words = words.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            fixes = rules.fixes,
            neverChange = rules.neverChange,
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
)

object PersonalImport {
    fun merge(current: PersonalRules, currentWords: Collection<String>, backup: PersonalBackup): ImportResult {
        val knownWords = currentWords.map { keyOf(it) }.toHashSet()
        val newWords = backup.words
            .map { it.trim() }
            .filter { it.isNotEmpty() && knownWords.add(keyOf(it)) }

        var rules = current
        var fixes = 0
        for (fix in backup.fixes) {
            if (fix.typed.isBlank() || fix.meant.isBlank() || rules.fixFor(fix.typed) != null) continue
            if (fix.typed == fix.meant) continue
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
        return ImportResult(rules, newWords, fixes, blocks)
    }
}
