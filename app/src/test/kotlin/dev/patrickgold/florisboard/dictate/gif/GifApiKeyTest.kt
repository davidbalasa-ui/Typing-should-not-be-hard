/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which key a GIF request uses: the user's own when saved, else the one built into the app. */
class GifApiKeyTest {
    @Test
    fun `no user key uses the built-in key`() {
        assertEquals("builtin123", GifApiKey.effective(userKey = "", builtInKey = "builtin123"))
    }

    @Test
    fun `a saved user key wins over the built-in key`() {
        assertEquals("mine456", GifApiKey.effective(userKey = "mine456", builtInKey = "builtin123"))
    }

    @Test
    fun `a whitespace-only user key counts as none`() {
        assertEquals("builtin123", GifApiKey.effective(userKey = "  \n", builtInKey = "builtin123"))
    }

    @Test
    fun `keys are trimmed`() {
        assertEquals("mine456", GifApiKey.effective(userKey = " mine456 ", builtInKey = "builtin123"))
        assertEquals("builtin123", GifApiKey.effective(userKey = "", builtInKey = " builtin123\n"))
    }

    @Test
    fun `no key anywhere stays blank, so nothing is sent`() {
        assertEquals("", GifApiKey.effective(userKey = "", builtInKey = ""))
        val provider = KlipyGifProvider(apiKeyProvider = { GifApiKey.effective("", "") })
        assertFalse(provider.isConfigured)
    }

    @Test
    fun `the provider is configured by the built-in key alone`() {
        val provider = KlipyGifProvider(apiKeyProvider = { GifApiKey.effective("", "builtin123") })
        assertTrue(provider.isConfigured)
    }

    @Test
    fun `a local build has no built-in key`() {
        // Unit tests run without KLIPY_API_KEY (CI passes it only to main's release build), so this
        // also proves the secret does not leak into ordinary builds.
        assertEquals("", GifApiKey.builtIn)
        assertFalse(GifApiKey.hasBuiltIn)
    }
}
