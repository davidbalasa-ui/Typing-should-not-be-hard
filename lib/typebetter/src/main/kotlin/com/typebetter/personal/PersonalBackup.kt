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

    /** Returns null when [text] is not a rules file, so a damaged file can fall back to its copy. */
    fun decodeRules(text: String): PersonalRules? = try {
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
        // The backup knows which starter fixes its owner kept. Starter fixes on this phone that the backup
        // does not have were deleted there (the pack is re-added automatically on a new phone), so they go.
        // Only pack entries are touched; nothing the user made is ever removed by an import.
        // Only when the backup was made with this pack version or newer: an older backup cannot know about
        // entries a newer pack added.
        if (backup.starterPackVersion != null && backup.starterPackVersion >= StarterPack.VERSION) {
            val keptStarter = backup.fixes.filter { it.source == Source.STARTER }.map { it.key }.toSet()
            rules = rules.copy(fixes = rules.fixes.filterNot { it.source == Source.STARTER && it.key !in keptStarter })
        }
        var fixes = 0
        for (fix in backup.fixes) {
            if (fix.typed.isBlank() || fix.meant.isBlank() || rules.fixFor(fix.typed) != null) continue
            if (fix.typed.trim().replace(Regex("\\s+"), " ") == fix.meant.trim()) continue
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
        val packWords = backup.starterWords.orEmpty()
        if (packWords.isNotEmpty()) {
            rules = rules.copy(
                starterWords = (rules.starterWords + packWords).distinctBy { keyOf(it) },
                starterPackVersion = maxOf(rules.starterPackVersion, backup.starterPackVersion ?: 0),
            )
        }
        return ImportResult(rules, newWords, fixes, blocks)
    }
}
