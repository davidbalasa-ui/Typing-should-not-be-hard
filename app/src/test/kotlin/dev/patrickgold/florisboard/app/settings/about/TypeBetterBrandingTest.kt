/*
 * Copyright (C) 2026 The TypeBetter contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings.about

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TypeBetter shows its own name and links, and credits Dictate and FlorisBoard (issue: About still showed
 * Dictate's signature, website, "More apps" and upstream links in v108). Reads the resource files directly,
 * so a string that slips back to the upstream brand fails here rather than on the phone.
 */
class TypeBetterBrandingTest {

    private val appDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            File(dir, "app/src/main").takeIf { it.isDirectory }?.let { return@lazy File(dir, "app") }
            File(dir, "src/main/res").takeIf { it.isDirectory }?.let { return@lazy dir }
            dir = dir.parentFile
        }
        error("could not locate the app module")
    }

    /** name -> value of every <string> in one resources file. */
    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as org.w3c.dom.Element
            e.getAttribute("name") to e.textContent
        }
    }

    private val res get() = File(appDir, "src/main/res")
    private val english get() = strings(File(res, "values/strings.xml"))
    private val links get() = strings(File(res, "values/strings_dont_translate.xml"))

    @Test
    fun `project links point at the TypeBetter repository`() {
        val repo = "https://github.com/davidbalasa-ui/Typing-should-not-be-hard"
        for (key in listOf(
            "florisboard__repo_url", "florisboard__issue_tracker_url", "florisboard__privacy_policy_url",
            "florisboard__changelog_url", "florisboard__commit_by_hash_url", "florisboard__internal_key_codes_url",
        )) {
            assertTrue(links.getValue(key).startsWith(repo), "$key = ${links[key]}")
        }
        assertTrue("florisboard__website_url" !in links, "Dictate's website link is back")
    }

    @Test
    fun `credits name Dictate, its developer and FlorisBoard, with their links`() {
        val en = english
        assertTrue(en.getValue("about__credits_dictate__summary").contains("Dictate"))
        assertTrue(en.getValue("about__credits_dictate__summary").contains("DevEmperor"))
        assertTrue(en.getValue("about__credits_floris__summary").contains("FlorisBoard"))
        assertTrue(en.getValue("about__credits_donate__summary").contains("DevEmperor"))
        assertEquals("https://github.com/DevEmperor/Dictate", links.getValue("dictate__upstream_repo_url"))
        assertEquals("https://github.com/florisboard/florisboard", links.getValue("florisboard__upstream_repo_url"))
    }

    @Test
    fun `no translation shows the upstream signature, More apps or the other apps`() {
        val banned = Regex("(?i)sayova|tunertogo|soli deo|contact@devemperor|dictatekeyboard\\.com(?!/delete)")
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.forEach { dir ->
            val file = File(dir, "strings.xml").takeIf { it.isFile } ?: return@forEach
            strings(file).forEach { (name, value) ->
                assertTrue(!name.startsWith("about__more_apps"), "${dir.name}/$name still exists")
                assertTrue(!banned.containsMatchIn(value), "${dir.name}/$name: $value")
            }
        }
    }

    @Test
    fun `English names the app TypeBetter, except Dictate Cloud and the Dictate settings screen`() {
        // Kept on purpose: "Dictate Cloud" is Dictate's own paid service; "Dictate" is also the verb and the
        // name of the voice settings screen ("Settings → Dictate"); history says "Dictate 3"; the 4.0 changelog
        // describes Dictate's own release.
        val allowed = Regex("Dictate Cloud|[→›▸←] Dictate|Dictate [←→›▸]|Dictate 3\\b")
        val verbs = setOf(
            "quick_action__ime_ui_mode_dictate", "setup__floating_button__title", "dictate__cloud_intro_title",
            "apptour52__intro_title", "dictate__title", "dictate__cloud_activate_btn", "changelog__current",
        )
        val credits = Regex("^(about__credits_|about__tagline|apptour51__outro_body_credit|changelog__donate_credit)")
        english.filterKeys { it !in verbs && !credits.containsMatchIn(it) }.forEach { (name, value) ->
            val rest = allowed.replace(value, "")
            assertTrue(!Regex("(?<![A-Za-z])Dictate(?![A-Za-z])").containsMatchIn(rest), "$name: $value")
        }
    }

    @Test
    fun `every full translation carries the credits, so About is not half English`() {
        val credits = listOf(
            "about__tagline", "about__credits__title", "about__credits_dictate__title", "about__credits_dictate__summary",
            "about__credits_floris__summary", "about__credits_donate__title", "about__credits_donate__summary",
            "changelog__donate_credit", "apptour51__outro_body_credit",
        )
        for (locale in listOf("de", "fr", "es", "it", "ru", "ja", "zh-rCN", "ar")) {
            val values = strings(File(res, "values-$locale/strings.xml"))
            for (key in credits) {
                assertTrue(key in values, "values-$locale lacks $key")
                if (key != "about__credits__title") {
                    assertTrue(
                        Regex("Dictate|DevEmperor").containsMatchIn(values.getValue(key)),
                        "values-$locale/$key names neither Dictate nor DevEmperor",
                    )
                }
            }
        }
    }

    @Test
    fun `the shipped NOTICE matches the repository NOTICE and keeps both upstream notices`() {
        val shipped = File(appDir, "src/main/assets/license/notice.txt").readText()
        val root = File(appDir.parentFile, "NOTICE").readText()
        assertEquals(root, shipped, "app/src/main/assets/license/notice.txt must be a copy of NOTICE")
        assertTrue(shipped.contains("Copyright 2024-2026 DevEmperor"))
        assertTrue(shipped.contains("The FlorisBoard Contributors"))
    }
}
