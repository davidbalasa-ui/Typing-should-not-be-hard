package com.typebetter.personal

/**
 * David's starter pack: the words and fixes found while planning TypeBetter (November 2026), from his
 * Twin Haven campaign notes, the Dungeons, Dragons and Defense game, the cursor bridge and the words he
 * uses with Claude. Switched on at first start; every entry can be deleted, and switching the pack off
 * removes only what the pack added.
 *
 * Words are ordered by how much help they need, because the speech model's prompt takes them in order:
 * campaign names first, then project and tech words, then game terms.
 */
object StarterPack {
    /** Bump when the lists change, so the new entries reach phones that applied an older pack. */
    const val VERSION = 1

    val words: List<String> = listOf(
        // Twin Haven — player characters and the names dictation gets wrong most
        "Xylos", "Kasmyra", "Kas", "Zephaniah", "Niah", "Rory", "Fido", "Diggle", "Nephryon", "WeaveMaker",
        "Feywild", "TwinHaven", "Reygar", "Lorelei",
        // Main NPCs
        "Lyla", "Leander", "Sheldon", "Zerice", "Cerberus", "Strahd", "Cyan'k", "Solara", "Lliira", "Fierna",
        "Grog", "Erilion", "Charyssa", "Gorn", "Belial", "Zemyna", "Eben", "Sune", "Erinyes", "Ilia",
        "Glumfoot", "Titania", "Arden", "Hoofmoon", "Abaddon", "McAllister", "Andra", "Yaga",
        // Other NPCs
        "Sutekh", "Shadrix", "Silverquill", "Thradrix", "Ba'al", "Bosnecker", "Feyona", "Talsdori",
        "Flibberton", "McGuckget", "Ristik", "Wilva",
        // Places
        "Volgrad", "Arcadia", "Peccuvo", "Phlegethos", "Lantis", "Krezakar'thuul", "Shadowfell", "Avernus",
        "Eldoria", "Hegsheim", "Bondiberg", "Vancelonia", "Sumaba", "Minauros", "Malbolge", "Stygia",
        "Maladomini",
        // Factions, items and campaign terms
        "Arcadians", "Arcadian", "Mutualists", "MiddleMen", "Vesper", "Sepulchre", "Gutterglider",
        "Inkomancy", "Residuum", "Stunnock", "KeyFlame", "statblock", "homebrew", "compendium", "subclass",
        "handouts", "NPC", "NPCs", "PC", "DM", "CR", "AC", "HP", "WPM",
        // Projects and tech
        "Claude", "Anthropic", "TypeBetter", "Dictate", "LeanType", "Obtainium", "MCP",
        "Vercel", "Supabase", "ChatGPT", "OpenAI", "Auth0", "OAuth", "JWKS", "JWT", "Next.js", "Deno",
        "Playwright", "GitHub", "Spotify", "DDD", "PR", "APK", "repo", "zod", "tsx", "agentId", "runId",
        // Dungeons, Dragons and Defense
        "Halfling", "Beastkeeper", "Sparkcaller", "Hexbinder", "Lightfoot", "Holdfast", "Creel", "Cookfire",
        "Cookhouse", "Garth", "Smithy", "Guardhouse", "Owlbear", "Griffon", "Stirge", "Treant", "Hatchling",
        "Cinders", "Sigil", "Regalia", "Forcecage", "Counterspell", "Caltrop", "Pylon", "Affixes", "Mythic",
        "Ascendant", "Astra", "Gemini", "Blender",
    )

    private fun auto(typed: String, meant: String) = Fix(typed, meant, FixMode.AUTO, Source.STARTER)
    private fun suggest(typed: String, meant: String) = Fix(typed, meant, FixMode.SUGGEST, Source.STARTER)

    /**
     * Spelling variants found in the notes and what dictation made of the names. A wrong word that is
     * also a real word ("silos", "corn") only suggests; a made-up one ("xylose") is fixed.
     */
    val fixes: List<Fix> = listOf(
        // What dictation wrote in the session transcripts
        suggest("silos", "Xylos"),
        auto("xylose", "Xylos"),
        auto("zylos", "Xylos"),
        auto("weave maker", "WeaveMaker"),
        auto("we've maker", "WeaveMaker"),
        auto("nephron", "Nephryon"),
        auto("nephrion", "Nephryon"),
        auto("fey wild", "Feywild"),
        auto("twin heaven", "TwinHaven"),
        suggest("arcades", "Arcadia"),
        suggest("corn", "Gorn"),
        suggest("heaven", "Haven"),
        auto("regar", "Reygar"),
        // Names spelled more than one way in the notes
        auto("kasmira", "Kasmyra"),
        auto("kazmira", "Kasmyra"),
        auto("beliel", "Belial"),
        auto("abbadon", "Abaddon"),
        auto("minaurus", "Minauros"),
        auto("nephryan", "Nephryon"),
        auto("gorg", "Grog"),
        auto("twinhavan", "TwinHaven"),
        auto("twinhaven", "TwinHaven"),
        suggest("lorali", "Lorelei"),
        suggest("fredrick", "Frederick"),
        // US spelling, as the game writes it
        auto("defence", "defense"),
        auto("armour", "armor"),
        // Tech words as phones and dictation mangle them
        auto("versel", "Vercel"),
        auto("vercelle", "Vercel"),
        suggest("mpc", "MCP"),
        auto("super base", "Supabase"),
        auto("superbase", "Supabase"),
        auto("chat gpt", "ChatGPT"),
        auto("open ai", "OpenAI"),
        auto("next js", "Next.js"),
        auto("o auth", "OAuth"),
        auto("key flame", "KeyFlame"),
        suggest("keyframe", "KeyFlame"),
        auto("spotty fi", "Spotify"),
        // Game words dictation splits in two
        auto("owl bear", "Owlbear"),
        auto("force cage", "Forcecage"),
        auto("spark caller", "Sparkcaller"),
        auto("half ling", "Halfling"),
        auto("combat ant", "combatant"),
        auto("home brew", "homebrew"),
        // Everyday misspellings from the plan
        auto("recieve", "receive"),
        auto("definately", "definitely"),
    )

    /**
     * Adds the pack to [rules]. Fixes the user already has for the same word are left alone, and a pack
     * that was already applied at this [VERSION] adds nothing, so deleting a starter entry is permanent —
     * unless [force] is set, which is what switching the pack back on in settings does.
     *
     * @return the new rules and the words to put into the personal dictionary (the caller adds only
     *   those it did not have, and reports them back through [recordWordsAdded]).
     */
    fun apply(rules: PersonalRules, force: Boolean = false): Pair<PersonalRules, List<String>> {
        if (!force && rules.starterPackVersion >= VERSION) return rules to emptyList()
        var updated = rules
        for (fix in fixes) {
            if (updated.fixFor(fix.typed) != null || updated.isBlocked(fix.typed, fix.meant)) continue
            updated = updated.withFix(fix)
        }
        return updated.copy(starterPackVersion = VERSION) to words
    }

    fun recordWordsAdded(rules: PersonalRules, added: List<String>): PersonalRules =
        rules.copy(starterWords = (rules.starterWords + added).distinctBy { keyOf(it) })

    /**
     * Takes the pack back out: its fixes, and the words it added (returned, for the caller to delete from
     * the personal dictionary). The user's own entries are untouched. Leaves the version set, so the pack
     * does not come back by itself on the next start.
     */
    fun remove(rules: PersonalRules): Pair<PersonalRules, List<String>> {
        val words = rules.starterWords
        return rules.copy(fixes = rules.fixes.filterNot { it.source == Source.STARTER }, starterWords = emptyList()) to words
    }

    /** True while any starter entry is still in place. */
    fun isActive(rules: PersonalRules): Boolean =
        rules.fixes.any { it.source == Source.STARTER } || rules.starterWords.isNotEmpty()
}
