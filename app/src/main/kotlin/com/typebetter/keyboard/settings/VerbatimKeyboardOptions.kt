package com.typebetter.keyboard.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PlatformImeOptions
import com.typebetter.keyboard.VerbatimField

/**
 * For the boxes where a correction is typed: no capital, no autocorrect from the app, and the
 * [VerbatimField] marker so TypeBetter's own keyboard does not swap the word either.
 */
internal val VerbatimKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    platformImeOptions = PlatformImeOptions(VerbatimField.MARKER),
)
