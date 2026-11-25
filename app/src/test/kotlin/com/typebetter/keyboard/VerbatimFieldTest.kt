package com.typebetter.keyboard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerbatimFieldTest {
    @Test
    fun `the marker is found alone or among other options, and nowhere else`() {
        assertTrue(VerbatimField.isVerbatim(VerbatimField.MARKER))
        assertTrue(VerbatimField.isVerbatim("nm, ${VerbatimField.MARKER}"))
        assertFalse(VerbatimField.isVerbatim(null))
        assertFalse(VerbatimField.isVerbatim(""))
        assertFalse(VerbatimField.isVerbatim("com.typebetter.keyboard.verbatimish"))
    }
}
