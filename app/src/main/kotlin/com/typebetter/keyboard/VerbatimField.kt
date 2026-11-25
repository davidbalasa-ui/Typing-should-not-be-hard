package com.typebetter.keyboard

/**
 * A text field whose words must reach the app exactly as typed: the boxes where a correction is entered
 * ("When I type: dor"). There the keyboard still suggests, but Space never swaps a word by itself, or
 * typing the misspelling you want to fix would fix it before it is saved.
 *
 * Marked through `EditorInfo.privateImeOptions`, the field an app uses to talk to a keyboard. Android's
 * `TYPE_TEXT_FLAG_AUTO_CORRECT` cannot carry this: ordinary text fields leave it unset too, so reading it
 * would switch autocorrect off almost everywhere.
 */
object VerbatimField {
    /** The marker; one entry of the comma-separated `privateImeOptions`. */
    const val MARKER = "com.typebetter.keyboard.verbatim"

    fun isVerbatim(privateImeOptions: String?): Boolean =
        privateImeOptions?.split(',')?.any { it.trim() == MARKER } == true
}
