/*
 * Copyright (C) 2026 TypeBetter contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.dictate.provider

/**
 * Packs the speech segments a VAD reports into decode windows.
 *
 * Why this exists: Whisper's encoder always works on a full 30 s window, padding shorter audio with
 * silence. Decoding every VAD segment on its own (what the long-audio path used to do) therefore paid a
 * whole 30 s encoder pass for every breath-sized phrase: a one-minute dictation with pauses every few
 * seconds became fifteen or twenty encoder passes instead of three. Packing segments together until a
 * window is full is the same transcript for a fraction of the work.
 *
 * Positions are sample offsets on one timeline (the recording from its first sample). Each segment is
 * widened by [padSamples] on both sides so a word's edges are never clipped; overlapping padded spans
 * merge, so a short pause between two segments stays in as a natural pause while a long one shrinks to
 * at most two pads. A window closes as soon as it reaches [targetSamples], or before a segment that
 * would push it past [maxSamples]. Windows never overlap: a new window starts no earlier than where the
 * last closed one ended, so no audio is transcribed twice.
 *
 * Pure bookkeeping with no audio and no native code, so it is unit tested on the JVM.
 */
internal class SpeechWindowPlanner(
    private val targetSamples: Long,
    private val maxSamples: Long,
    private val padSamples: Long,
) {
    init {
        require(targetSamples > 0 && maxSamples >= targetSamples && padSamples >= 0) {
            "bad planner sizes target=$targetSamples max=$maxSamples pad=$padSamples"
        }
    }

    /** A span of the timeline, [start] inclusive to [end] exclusive. */
    data class Span(val start: Long, val end: Long) {
        val length: Long get() = end - start
    }

    /** One decode's worth of audio: the spans to cut out of the timeline and join, in order. */
    class Window(val spans: List<Span>) {
        val length: Long get() = spans.sumOf { it.length }

        /**
         * The window's audio, cut from [samples] whose first element sits at timeline position [offset].
         * Spans are clipped to what [samples] actually holds: a pad that runs past the end of the recording
         * (or of what has been captured so far) simply ends where the audio does. Only the first [length]
         * elements of [samples] count, for a buffer with spare room at its end.
         */
        fun copyFrom(samples: FloatArray, offset: Long = 0L, length: Int = samples.size): FloatArray {
            val limit = offset + length
            var total = 0
            for (s in spans) {
                val a = maxOf(s.start, offset)
                val b = minOf(s.end, limit)
                if (b > a) total += (b - a).toInt()
            }
            val out = FloatArray(total)
            var at = 0
            for (s in spans) {
                val a = maxOf(s.start, offset)
                val b = minOf(s.end, limit)
                if (b > a) {
                    samples.copyInto(out, at, (a - offset).toInt(), (b - offset).toInt())
                    at += (b - a).toInt()
                }
            }
            return out
        }
    }

    private val open = ArrayList<Span>()
    private var openLength = 0L

    /** Where the last closed window ended; nothing before it is handed out again. */
    var floor: Long = 0L
        private set

    /** Whether a window is being filled. */
    val hasOpenWindow: Boolean get() = open.isNotEmpty()

    /** Speech handed to [add] so far, unpadded. */
    var speechSamples: Long = 0L
        private set

    /**
     * Adds the speech segment [start] until [end] and returns the windows that closed because of it (zero,
     * one or two), in timeline order.
     */
    fun add(start: Long, end: Long): List<Window> {
        if (end <= start) return emptyList()
        speechSamples += end - start
        val closed = ArrayList<Window>(2)
        if (open.isNotEmpty() && openLength + growth(start, end) > maxSamples) {
            closed += close()
        }
        append(start, end)
        if (openLength >= targetSamples) closed += close()
        return closed
    }

    /** Closes and returns whatever window is open, or null if none is. Called once the audio has ended. */
    fun flush(): Window? = if (open.isEmpty()) null else close()

    private fun padded(start: Long, end: Long): Span =
        Span(maxOf(start - padSamples, floor, 0L), end + padSamples)

    /** How much [openLength] grows if this segment is appended. */
    private fun growth(start: Long, end: Long): Long {
        val p = padded(start, end)
        val last = open.lastOrNull() ?: return p.length
        return if (p.start <= last.end) maxOf(0L, p.end - last.end) else p.length
    }

    private fun append(start: Long, end: Long) {
        val p = padded(start, end)
        val last = open.lastOrNull()
        if (last != null && p.start <= last.end) {
            if (p.end > last.end) {
                open[open.size - 1] = Span(last.start, p.end)
                openLength += p.end - last.end
            }
        } else {
            open += p
            openLength += p.length
        }
    }

    private fun close(): Window {
        val w = Window(open.toList())
        floor = open.last().end
        open.clear()
        openLength = 0L
        return w
    }
}
