/*
 * Copyright (C) 2026 Harith Alhamdani
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.espeakz.app

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Another installed engine, used for the second language of
 * [DualLanguage]. EspeakZ asks it to synthesize to a file and takes the audio
 * as it is produced (Android 7 and later report every chunk to the caller),
 * or reads the file when it is done on older versions. The audio is
 * converted to EspeakZ's format by [PcmConverter] and goes into EspeakZ's own
 * stream, so the second voice is heard where EspeakZ is, with no audio focus
 * or stream of its own.
 *
 * A request is queued with [request] well before it is needed, so that the
 * engine works on it while EspeakZ is still reading the text before it, and
 * played with [play] when its turn comes. The engine handles its queue in
 * order. [stop] may come from any thread and ends every queued request.
 */
class SecondEngine(context: Context, private val cacheDir: File, val packageName: String) {

    enum class Result {
        /** The engine read the text. */
        SPOKEN,
        /** The engine gave no audio; the caller reads the text itself. */
        FAILED,
        /** The request was stopped. */
        STOPPED,
    }

    /** One queued request and the engine's events for it. */
    class Job internal constructor(internal val id: String, internal val file: File) {
        internal val events = LinkedBlockingQueue<Any>()
        @Volatile internal var stopped = false
    }

    private class Begin(val sampleRate: Int, val format: Int, val channels: Int)
    private class Audio(val bytes: ByteArray)
    private class End(val ok: Boolean)

    private val ready = CountDownLatch(1)
    @Volatile private var initStatus = TextToSpeech.ERROR
    private val jobs = ConcurrentHashMap<String, Job>()

    private var voices: Map<String, android.speech.tts.Voice>? = null
    private var selectedVoice: String? = null

    /** Until when the engine is passed over, after it gave no answer at all. */
    @Volatile private var silentUntil = 0L

    /** True once the engine refused a request, such as after its process died: make a new one. */
    @Volatile var isBroken = false
        private set

    private val tts = TextToSpeech(context.applicationContext, { status ->
        initStatus = status
        ready.countDown()
    }, packageName)

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) = post(utteranceId, End(true))

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = post(utteranceId, End(false))

            override fun onError(utteranceId: String?, errorCode: Int) = post(utteranceId, End(false))

            override fun onStop(utteranceId: String?, interrupted: Boolean) = post(utteranceId, End(false))

            override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) =
                post(utteranceId, Begin(sampleRateInHz, audioFormat, channelCount))

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                if (audio != null) post(utteranceId, Audio(audio))
            }
        })
    }

    private fun post(utteranceId: String?, event: Any) {
        utteranceId?.let { jobs[it] }?.events?.offer(event)
    }

    /**
     * Queues [text] with the voice [voiceName], or in [locale] when the
     * engine no longer has that voice, at [rate] and [pitch] (1 is the
     * engine's normal). Returns null when the engine cannot take it.
     */
    @Synchronized
    fun request(text: String, voiceName: String, locale: Locale?, rate: Float, pitch: Float, volume: Float?): Job? {
        if (SystemClock.elapsedRealtime() < silentUntil) return null
        if (!ready.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) || initStatus != TextToSpeech.SUCCESS) {
            Log.w(TAG, "$packageName did not start")
            isBroken = true
            return null
        }
        // The voice, rate and pitch are copied into each request as it is
        // queued, so requests queued ahead keep their own.
        selectVoice(voiceName, locale)
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)

        val id = "espeakz-" + counter.incrementAndGet()
        val job = Job(id, File(cacheDir, "second-engine-$id.wav"))
        jobs[id] = job
        val params = Bundle()
        volume?.let { params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, it) }
        if (tts.synthesizeToFile(text, params, job.file, id) != TextToSpeech.SUCCESS) {
            Log.w(TAG, "$packageName refused the request")
            jobs.remove(id)
            isBroken = true
            return null
        }
        return job
    }

    /**
     * Hands [job]'s audio to [sink] as 16-bit mono at [outRate], louder or
     * softer by [gain], as the engine produces it. Blocks until it is done.
     */
    fun play(job: Job, outRate: Int, gain: Float, sink: (ShortArray, Int) -> Boolean): Result {
        try {
            var converter: PcmConverter? = null
            var delivered = false
            var ok = false
            while (true) {
                val event = job.events.poll(if (delivered) EVENT_TIMEOUT_MS else FIRST_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (event == null) {
                    Log.w(TAG, "$packageName stopped answering")
                    if (!delivered) {
                        // An engine that gives nothing at all, such as one
                        // asked for a voice it has not set up, would make
                        // every part wait for it: EspeakZ reads in its place
                        // for a while instead.
                        silentUntil = SystemClock.elapsedRealtime() + SILENT_PAUSE_MS
                    }
                    tts.stop()
                    break
                }
                if (job.stopped) return Result.STOPPED
                when (event) {
                    is Begin -> converter = PcmConverter(event.sampleRate, event.format, event.channels, outRate, sink, gain)
                    is Audio -> {
                        val c = converter
                            ?: PcmConverter(DEFAULT_RATE, AudioFormat.ENCODING_PCM_16BIT, 1, outRate, sink, gain).also { converter = it }
                        delivered = true
                        if (!c.write(event.bytes, 0, event.bytes.size)) {
                            stop()
                            return Result.STOPPED
                        }
                    }
                    is End -> {
                        ok = event.ok
                        break
                    }
                }
            }
            if (job.stopped) return Result.STOPPED
            if (!delivered && ok) {
                // Before Android 7 the audio only reaches the file.
                converter = readWav(job.file, outRate, gain, sink)
                delivered = converter != null
            }
            if (converter?.finish() == false) return Result.STOPPED
            return if (delivered) Result.SPOKEN else Result.FAILED
        } finally {
            jobs.remove(job.id)
            job.file.delete()
        }
    }

    /**
     * Has the engine read the language's own name once in the background and
     * throws the audio away, so that its voice is loaded before the first
     * real text: an engine that loads its voice on first use can take
     * seconds over it, Google's on a watch more than four.
     */
    fun warmUp(voiceName: String, locale: Locale?) {
        Thread({
            val text = locale?.let { it.getDisplayLanguage(it) }?.takeIf { it.isNotEmpty() } ?: return@Thread
            val job = request(text, voiceName, locale, 1f, 1f, null) ?: return@Thread
            play(job, DEFAULT_RATE, 0f) { _, _ -> true }
        }, "espeakz-second-warm-up").start()
    }

    /** Ends every queued request. */
    fun stop() {
        for (job in jobs.values) {
            job.stopped = true
            job.events.offer(End(false))
            job.file.delete()
        }
        jobs.clear()
        if (initStatus == TextToSpeech.SUCCESS) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun selectVoice(name: String, locale: Locale?) {
        if (name == selectedVoice) return
        val all = voices ?: tts.voices?.associateBy { it.name }?.also { voices = it }
        val voice = all?.get(name)
        if (voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS) {
            selectedVoice = name
            return
        }
        Log.w(TAG, "$packageName has no voice $name; using its voice for $locale")
        voices = null
        selectedVoice = null
        locale?.let { tts.language = it }
    }

    /** Feeds a finished WAV file through a new converter, or returns null when it holds no audio. */
    private fun readWav(file: File, outRate: Int, gain: Float, sink: (ShortArray, Int) -> Boolean): PcmConverter? {
        if (!file.exists() || file.length() <= 44) return null
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt(0) != RIFF || buffer.getInt(8) != WAVE) return null
        var position = 12
        var rate = DEFAULT_RATE
        var channels = 1
        var format = AudioFormat.ENCODING_PCM_16BIT
        while (position + 8 <= buffer.limit()) {
            val id = buffer.getInt(position)
            val size = buffer.getInt(position + 4)
            val body = position + 8
            if (id == FMT) {
                val code = buffer.getShort(body).toInt()
                channels = buffer.getShort(body + 2).toInt().coerceAtLeast(1)
                rate = buffer.getInt(body + 4)
                val bits = buffer.getShort(body + 14).toInt()
                format = when {
                    code == 3 -> AudioFormat.ENCODING_PCM_FLOAT
                    bits == 8 -> AudioFormat.ENCODING_PCM_8BIT
                    else -> AudioFormat.ENCODING_PCM_16BIT
                }
            } else if (id == DATA) {
                val length = minOf(size, buffer.limit() - body)
                if (length <= 0) return null
                val converter = PcmConverter(rate, format, channels, outRate, sink, gain)
                converter.write(buffer.array(), body, length)
                return converter
            }
            position = body + size + (size and 1)
        }
        return null
    }

    companion object {
        private const val TAG = "SecondEngine"
        private const val INIT_TIMEOUT_MS = 5000L
        private const val EVENT_TIMEOUT_MS = 10000L
        private const val FIRST_EVENT_TIMEOUT_MS = 5000L
        private const val SILENT_PAUSE_MS = 60000L
        private const val DEFAULT_RATE = 22050
        private const val RIFF = 0x46464952 // "RIFF"
        private const val WAVE = 0x45564157 // "WAVE"
        private const val FMT = 0x20746d66 // "fmt "
        private const val DATA = 0x61746164 // "data"
        private val counter = AtomicInteger()
    }
}
