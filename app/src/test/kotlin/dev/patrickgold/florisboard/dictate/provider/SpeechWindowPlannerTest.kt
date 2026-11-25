/*
 * Copyright (C) 2026 TypeBetter contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import dev.patrickgold.florisboard.dictate.provider.SpeechWindowPlanner.Span
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val SR = 16_000L

/** Seconds to samples, so the cases read in the unit people think in. */
private fun s(seconds: Double): Long = (seconds * SR).toLong()

class SpeechWindowPlannerTest : FunSpec({

    fun whisperPlanner(target: Double = 28.0) = SpeechWindowPlanner(
        targetSamples = s(target),
        maxSamples = s(28.0),
        padSamples = s(0.2),
    )

    test("a minute of short phrases packs into three windows, not one decode per phrase") {
        // 20 phrases of 2.5 s with 0.5 s breaths: the old path decoded each phrase alone (20 encoder passes).
        val p = whisperPlanner()
        val windows = ArrayList<SpeechWindowPlanner.Window>()
        var t = 0.0
        repeat(20) {
            windows += p.add(s(t), s(t + 2.5))
            t += 3.0
        }
        p.flush()?.let { windows += it }
        windows.size shouldBe 3
        windows.forEach { (it.length <= s(28.0)) shouldBe true }
    }

    test("windows never overlap and keep timeline order") {
        val p = whisperPlanner(target = 5.0)
        val windows = ArrayList<SpeechWindowPlanner.Window>()
        var t = 0.0
        repeat(30) {
            windows += p.add(s(t), s(t + 1.3))
            t += 1.5
        }
        p.flush()?.let { windows += it }
        val spans = windows.flatMap { it.spans }
        spans.zipWithNext().forEach { (a, b) -> (a.end <= b.start) shouldBe true }
    }

    test("every speech sample lands in exactly one window") {
        val p = whisperPlanner(target = 4.0)
        val segments = listOf(0.5 to 2.0, 2.3 to 4.9, 9.0 to 9.6, 9.8 to 14.0, 20.0 to 21.0)
        val windows = ArrayList<SpeechWindowPlanner.Window>()
        segments.forEach { (a, b) -> windows += p.add(s(a), s(b)) }
        p.flush()?.let { windows += it }
        val covered = windows.flatMap { it.spans }
        for ((a, b) in segments) {
            var x = s(a)
            while (x < s(b)) {
                covered.count { x >= it.start && x < it.end } shouldBe 1
                x += 800
            }
        }
    }

    test("a short pause stays in, a long pause shrinks to two pads") {
        val p = whisperPlanner()
        p.add(s(1.0), s(3.0))
        p.add(s(3.3), s(5.0)) // 0.3 s pause: padded spans touch, so they merge
        p.add(s(15.0), s(17.0)) // 10 s of silence between: cut out
        val w = p.flush()!!
        w.spans shouldBe listOf(Span(s(0.8), s(5.2)), Span(s(14.8), s(17.2)))
    }

    test("a window closes before a segment that would push it past the maximum") {
        val p = SpeechWindowPlanner(targetSamples = s(28.0), maxSamples = s(28.0), padSamples = 0)
        p.add(s(0.0), s(20.0)).size shouldBe 0
        val closed = p.add(s(21.0), s(30.0))
        closed.size shouldBe 1
        closed[0].spans shouldBe listOf(Span(0, s(20.0)))
        p.flush()!!.spans shouldBe listOf(Span(s(21.0), s(30.0)))
    }

    test("a window closes as soon as it reaches the target") {
        val p = SpeechWindowPlanner(targetSamples = s(12.0), maxSamples = s(28.0), padSamples = 0)
        p.add(s(0.0), s(6.0)).size shouldBe 0
        val closed = p.add(s(6.5), s(13.0))
        closed.size shouldBe 1
        p.hasOpenWindow shouldBe false
        p.floor shouldBe s(13.0)
        p.flush() shouldBe null
    }

    test("the next window's lead-in pad never reaches back into the last window") {
        val p = SpeechWindowPlanner(targetSamples = s(2.0), maxSamples = s(28.0), padSamples = s(0.2))
        p.add(s(0.0), s(2.0)).size shouldBe 1 // closes at 2.2 (end pad)
        p.add(s(2.25), s(3.0))
        p.flush()!!.spans.first().start shouldBe s(2.2)
    }

    test("no speech means no window") {
        val p = whisperPlanner()
        p.add(s(3.0), s(3.0)).size shouldBe 0
        p.flush() shouldBe null
        p.speechSamples shouldBe 0L
    }

    test("copyFrom joins the spans and clips to the audio actually there") {
        val audio = FloatArray(100) { it.toFloat() }
        val w = SpeechWindowPlanner.Window(listOf(Span(10, 13), Span(95, 110)))
        w.copyFrom(audio).toList() shouldBe listOf(10f, 11f, 12f, 95f, 96f, 97f, 98f, 99f)
        // The same timeline seen through a buffer that starts at position 50 and only holds 40 samples.
        val tail = FloatArray(60) { (it + 50).toFloat() }
        w.copyFrom(tail, offset = 50, length = 40).toList() shouldBe emptyList()
        SpeechWindowPlanner.Window(listOf(Span(60, 63))).copyFrom(tail, offset = 50, length = 40).toList() shouldBe
            listOf(60f, 61f, 62f)
    }

    test("recognizer threads: half the cores, between 2 and 4") {
        LocalTranscriptionProvider.defaultThreads(1) shouldBe 2
        LocalTranscriptionProvider.defaultThreads(4) shouldBe 2
        LocalTranscriptionProvider.defaultThreads(6) shouldBe 3
        LocalTranscriptionProvider.defaultThreads(8) shouldBe 4
        LocalTranscriptionProvider.defaultThreads(12) shouldBe 4
    }
})
