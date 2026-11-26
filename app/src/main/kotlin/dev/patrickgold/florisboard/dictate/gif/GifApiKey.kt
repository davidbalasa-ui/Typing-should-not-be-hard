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

import dev.patrickgold.florisboard.BuildConfig

/**
 * Which KLIPY key a GIF request uses. Release builds of TypeBetter carry a built-in key (from the
 * `KLIPY_API_KEY` repository secret, see app/build.gradle.kts), so GIF search works without the user
 * signing up anywhere. A key the user saved in Settings always wins over it, so anyone who prefers
 * their own quota keeps it. Builds without the secret have an empty built-in key and behave exactly as
 * before: no key, no GIF requests.
 */
object GifApiKey {
    /** The key compiled into this build; blank when the build had no `KLIPY_API_KEY`. */
    val builtIn: String
        get() = BuildConfig.KLIPY_BUILT_IN_API_KEY

    /** True when this build can search GIFs with no key from the user. */
    val hasBuiltIn: Boolean
        get() = builtIn.isNotBlank()

    /** The key to send: the user's own if they saved one, otherwise the built-in one (may be blank). */
    fun effective(userKey: String, builtInKey: String = builtIn): String =
        userKey.trim().ifBlank { builtInKey.trim() }
}
