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

import android.util.Log
import dev.patrickgold.florisboard.dictate.TranscriptJoin
import dev.patrickgold.florisboard.dictate.audio.AudioDecode
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Transcribes an on-device dictation while it is still being spoken, so the stop only has to wait for
 * the last few seconds instead of the whole recording.
 *
 * Before this, every word of a batch dictation was decoded after the stop: the phone sat idle for the
 * minute the user talked and then did a minute's work while they watched a spinner (about ten seconds
 * for a long dictation on Whisper Base). This takes the microphone's PCM as it arrives, finds the speech
 * with the same Silero VAD the long-audio path uses, packs it into windows of about [TARGET_SECONDS]
 * ([SpeechWindowPlanner]) and decodes each window on its own thread the moment it is full. By the stop,
 * everything up to the last window is already text.
 *
 * Only recordings longer than one decode's worth (28 s for Whisper) are answered from here. A shorter one
 * was always a single quick pass over the whole clip, and stays exactly that: same transcript as before.
 * Past that length the old path cut the audio at pauses too, so windows change nothing there but speed.
 *
 * It sees exactly what goes into the WAV (the recorder hands its sink the same frames it writes, and
 * neither gets the frames of a pause), so its text is a transcript of that file. It never replaces the
 * recording: the WAV is still written, kept for history and resend, and transcribed the normal way
 * whenever this cannot answer — it failed, it found no speech (the silence gate then decides as before),
 * or the dictation changed model or language underneath it.
 *
 * Threading: [feed] runs on the capture thread and only queues a copy. Everything else, including every
 * native call, runs on this decoder's own worker thread, which also owns and releases the VAD.
 */
class LocalEarlyDecoder(
    private val modelDir: File,
    vadFile: File,
    /** The language the recognizer is built for, exactly as [LocalTranscriptionProvider] will ask for it. */
    val language: String?,
    private val tighteningSymbols: String,
    private val numThreads: Int = LocalTranscriptionProvider.defaultThreads(),
) {
    /** The model this decoder transcribes with, by id. */
    val modelId: String get() = modelDir.name

    /** What the recording said, as far as this decoder can tell. */
    class Result(val text: String, val windows: Int, val tailMs: Long)

    private val maxSegmentSeconds: Int =
        LocalModelCatalog.byId(modelDir.name)?.maxSegmentSeconds ?: DEFAULT_MAX_SEGMENT_SECONDS
    private val maxPieceSamples = (maxSegmentSeconds + 1) * AudioDecode.TARGET_SAMPLE_RATE
    private val planner = SpeechWindowPlanner(
        targetSamples = minOf(TARGET_SECONDS, maxSegmentSeconds).toLong() * AudioDecode.TARGET_SAMPLE_RATE,
        maxSamples = LocalTranscriptionProvider.windowSamples(maxSegmentSeconds),
        padSamples = LocalTranscriptionProvider.PAD_SAMPLES,
    )

    private val queue = LinkedBlockingQueue<FloatArray>()
    private val done = CompletableDeferred<Result?>()
    @Volatile private var cancelled = false
    @Volatile private var ended = false
    @Volatile private var endedAtNanos = 0L

    // Worker-thread state: the audio not yet handed to a window, from timeline position [bufOrigin].
    private var buf = FloatArray(AudioDecode.TARGET_SAMPLE_RATE * 16)
    private var bufLen = 0
    private var bufOrigin = 0L
    private val text = StringBuilder()
    private var windows = 0
    /** Windows the planner closed that are not decoded yet, oldest first. */
    private val pending = ArrayDeque<SpeechWindowPlanner.Window>()

    /** At or below this much audio the normal single pass is used; see [work]. */
    private val singlePassSamples = maxSegmentSeconds.toLong() * AudioDecode.TARGET_SAMPLE_RATE
    /** Audio kept while no window is open: one maximal VAD segment, its pads and a margin. */
    private val retainSamples = (maxSegmentSeconds + 2).toLong() * AudioDecode.TARGET_SAMPLE_RATE +
        LocalTranscriptionProvider.PAD_SAMPLES

    private val worker = Thread({ work(vadFile) }, "stt-early-decoder").apply {
        isDaemon = true
        priority = Thread.NORM_PRIORITY - 1
    }

    init {
        worker.start()
    }

    /**
     * A captured frame of mono PCM16 little-endian audio, as [dev.patrickgold.florisboard.dictate.audio
     * .RecordingController] hands its sink. Copied before returning, since the recorder reuses [pcm].
     */
    fun feed(pcm: ByteArray, len: Int) {
        if (cancelled || ended) return
        val n = len / 2
        if (n <= 0) return
        val out = FloatArray(n)
        var j = 0
        for (i in 0 until n) {
            val lo = pcm[j].toInt() and 0xFF
            val hi = pcm[j + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort() / 32768f
            j += 2
        }
        queue.offer(out)
    }

    /**
     * The recording has ended: transcribes what is left and returns the whole text, or null when this
     * decoder has no answer and the recording must take the normal path (failure, no speech found).
     * Suspends until the last window is decoded.
     */
    suspend fun finish(): Result? {
        if (cancelled) return null
        endedAtNanos = System.nanoTime()
        ended = true
        queue.offer(END)
        return done.await()
    }

    /** Throws the work away. Safe to call at any time and more than once; the worker frees its VAD. */
    fun cancel() {
        cancelled = true
        queue.offer(END)
        done.complete(null)
    }

    private fun work(vadFile: File) {
        var vad: com.k2fsa.sherpa.onnx.Vad? = null
        try {
            vad = LocalTranscriptionProvider.newSegmentVad(vadFile, maxSegmentSeconds, numThreads = 1)
            val window = FloatArray(LocalTranscriptionProvider.VAD_WINDOW)
            var vadFill = 0
            while (!cancelled) {
                val chunk = queue.poll(POLL_MS, TimeUnit.MILLISECONDS) ?: continue
                if (chunk === END) break
                append(chunk)
                // The VAD gets whole 512-sample windows, as on the batch path, so it cuts the same way.
                var i = 0
                while (i < chunk.size) {
                    val take = minOf(window.size - vadFill, chunk.size - i)
                    chunk.copyInto(window, vadFill, i, i + take)
                    vadFill += take
                    i += take
                    if (vadFill == window.size) {
                        vad.acceptWaveform(window)
                        vadFill = 0
                        LocalTranscriptionProvider.drainSegments(vad) { s, e -> pending += planner.add(s, e) }
                    }
                }
                // Only a recording past one decode's worth is decoded early: anything shorter takes the
                // single whole-clip pass after the stop, exactly as before, which is already quick.
                if (captured > singlePassSamples) decodeReady(final = false)
                if (cancelled) return
            }
            if (cancelled) return
            if (captured <= singlePassSamples) {
                // Short recording: no answer from here. The normal path transcribes it in one pass.
                done.complete(null)
                return
            }
            if (vadFill > 0) vad.acceptWaveform(window.copyOf(vadFill))
            vad.flush()
            LocalTranscriptionProvider.drainSegments(vad) { s, e -> pending += planner.add(s, e) }
            planner.flush()?.let { pending += it }
            decodeReady(final = true)
            if (cancelled) return
            val tailMs = (System.nanoTime() - endedAtNanos) / 1_000_000
            val joined = TranscriptJoin.tighten(text.toString(), tighteningSymbols).trim()
            Log.i(
                LocalTranscriptionProvider.LATENCY_TAG,
                "phase=earlyDecodeFinished windows=$windows speechMs=" +
                    "${planner.speechSamples * 1000 / AudioDecode.TARGET_SAMPLE_RATE} tailMs=$tailMs " +
                    "threads=$numThreads",
            )
            // No speech found: no answer from here. The normal path runs the silence gate and decides.
            done.complete(if (planner.speechSamples == 0L || joined.isBlank()) null else Result(joined, windows, tailMs))
        } catch (_: EarlyDecodeCancelled) {
            // Cancelled mid-window: nothing to report.
        } catch (t: Throwable) {
            Log.w(LocalTranscriptionProvider.LATENCY_TAG, "phase=earlyDecodeFailed", t)
        } finally {
            // From here on [feed] drops frames instead of queueing them for a worker that is gone.
            cancelled = true
            runCatching { vad?.release() }
            queue.clear()
            done.complete(null) // no-op when already answered
        }
    }

    /** Timeline position just past the last captured sample. */
    private val captured: Long get() = bufOrigin + bufLen

    /**
     * Decodes the closed windows whose audio has fully arrived. A window's end pad can reach past what was
     * captured when the VAD cuts continuous speech at its length limit; decoding it then would skip that
     * stretch for good, since the next window starts after it. At the end everything has arrived.
     */
    private fun decodeReady(final: Boolean) {
        while (pending.isNotEmpty() && !cancelled) {
            val w = pending.first()
            if (!final && w.spans.last().end > captured) return
            pending.removeFirst()
            decode(w)
        }
    }

    /** Keeps [chunk] until a window has used it, dropping audio no window can still ask for. */
    private fun append(chunk: FloatArray) {
        if (bufLen + chunk.size > buf.size) {
            // The earliest position anything may still read: a window waiting to be decoded, the one being
            // filled, or else the start of the longest segment the VAD could still report (it caps segments
            // at the model's limit, and needs a moment of silence to report one), with its lead-in pad.
            val keepFrom = pending.firstOrNull()?.spans?.first()?.start
                ?: planner.openStart
                ?: maxOf(planner.floor, captured - retainSamples)
            val drop = (keepFrom - bufOrigin).coerceIn(0L, bufLen.toLong()).toInt()
            if (drop > 0) {
                buf.copyInto(buf, 0, drop, bufLen)
                bufLen -= drop
                bufOrigin += drop
            }
            if (bufLen + chunk.size > buf.size) {
                buf = buf.copyOf(maxOf(buf.size * 2, bufLen + chunk.size))
            }
        }
        chunk.copyInto(buf, bufLen)
        bufLen += chunk.size
    }

    private fun decode(w: SpeechWindowPlanner.Window) {
        val samples = w.copyFrom(buf, bufOrigin, bufLen)
        if (samples.isEmpty()) return
        val startedNanos = System.nanoTime()
        val recognizer = LocalTranscriptionProvider.acquireRecognizer(modelDir, numThreads, language)
        try {
            LocalTranscriptionProvider.decodeCapped(recognizer, samples, maxPieceSamples, tighteningSymbols, text) {
                if (cancelled) throw EarlyDecodeCancelled()
            }
        } finally {
            LocalTranscriptionProvider.releaseRecognizer()
        }
        windows++
        Log.i(
            LocalTranscriptionProvider.LATENCY_TAG,
            "phase=earlyWindow n=$windows audioMs=${samples.size * 1000L / AudioDecode.TARGET_SAMPLE_RATE} " +
                "ms=${(System.nanoTime() - startedNanos) / 1_000_000} afterStop=$ended",
        )
    }

    private class EarlyDecodeCancelled : RuntimeException()

    companion object {
        /**
         * How much speech a window collects before it is decoded during the recording. Shorter means less
         * left over at the stop; longer means fewer passes and more context for each. Twelve seconds keeps
         * the work after the stop to about one window, and is still several sentences of context.
         */
        const val TARGET_SECONDS = 12
        private const val DEFAULT_MAX_SEGMENT_SECONDS = 28
        private const val POLL_MS = 250L
        private val END = FloatArray(0)
    }
}
