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

import android.media.AudioFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Turns another engine's audio into EspeakZ's: 16-bit mono at [outRate].
 *
 * The input may be 8-bit, 16-bit or float PCM with any number of channels at
 * any rate. Channels are mixed down, the rate is converted with a windowed
 * sinc filter, and the silence an engine puts before and after every
 * utterance is cut to [KEEP_MS], so that a word in the other language does
 * not stop the sentence around it.
 *
 * [sink] receives the converted samples and returns false once the caller no
 * longer accepts audio; [write] and [finish] then return false too. [gain]
 * makes the voice louder or softer.
 */
class PcmConverter(
    private val inRate: Int,
    private val encoding: Int,
    private val channels: Int,
    private val outRate: Int,
    private val sink: (ShortArray, Int) -> Boolean,
    private val gain: Float = 1f,
) {
    private val frameBytes = channels * when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    /** Bytes of a frame split across two writes. */
    private val partial = ByteArray(frameBytes)
    private var partialCount = 0

    private val resampling = inRate != outRate
    private val step = inRate.toDouble() / outRate
    private val table = if (resampling) filterTable(min(1.0, outRate.toDouble() / inRate) * CUTOFF) else null

    /** Input samples, starting with HALF - 1 zeros of history. */
    private var input = FloatArray(4096)
    private var inputCount = HALF - 1
    /** Where the next output sample falls in [input]. */
    private var position = (HALF - 1).toDouble()

    private val keep = outRate * KEEP_MS / 1000
    private var heardSound = false
    /** Quiet samples held back: before the first sound, or since the last one. */
    private var quiet = ShortArray(maxOf(4096, keep * 2))
    private var quietCount = 0

    private var out = ShortArray(2048)
    private var outCount = 0
    private var accepted = true

    fun write(bytes: ByteArray, offset: Int, length: Int): Boolean {
        var i = offset
        val end = offset + length
        while (accepted && i < end) {
            if (partialCount > 0 || end - i < frameBytes) {
                val take = min(frameBytes - partialCount, end - i)
                System.arraycopy(bytes, i, partial, partialCount, take)
                partialCount += take
                i += take
                if (partialCount == frameBytes) {
                    addInput(decodeFrame(partial, 0))
                    partialCount = 0
                }
                continue
            }
            addInput(decodeFrame(bytes, i))
            i += frameBytes
        }
        process()
        return flushOut()
    }

    /** Ends the input: delivers what the filter still holds, without the trailing silence. */
    fun finish(): Boolean {
        if (resampling) {
            repeat(HALF) { addInput(0f) }
            process()
        }
        if (!flushOut()) return false
        // Keep the start of the trailing silence as the pause after the word.
        if (heardSound) emitDirect(quiet, min(quietCount, keep))
        quietCount = 0
        return flushOut()
    }

    private fun decodeFrame(bytes: ByteArray, at: Int): Float {
        var sum = 0f
        for (c in 0 until channels) {
            sum += when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> ((bytes[at + c].toInt() and 0xff) - 128) * 256f
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    val p = at + c * 4
                    val bits = (bytes[p].toInt() and 0xff) or
                        ((bytes[p + 1].toInt() and 0xff) shl 8) or
                        ((bytes[p + 2].toInt() and 0xff) shl 16) or
                        (bytes[p + 3].toInt() shl 24)
                    Float.fromBits(bits) * 32767f
                }
                else -> {
                    val p = at + c * 2
                    ((bytes[p].toInt() and 0xff) or (bytes[p + 1].toInt() shl 8)).toShort().toFloat()
                }
            }
        }
        return sum / channels
    }

    private fun addInput(sample: Float) {
        if (!resampling) {
            emit(sample)
            return
        }
        if (inputCount == input.size) input = input.copyOf(input.size * 2)
        input[inputCount++] = sample
    }

    /** Produces every output sample the filter has enough input for, then drops used input. */
    private fun process() {
        val table = table ?: return
        while (true) {
            val base = floor(position).toInt()
            if (base + HALF >= inputCount) break
            val phase = ((position - base) * PHASES).toInt().coerceAtMost(PHASES - 1)
            val row = phase * TAPS
            var sum = 0f
            val first = base - HALF + 1
            for (k in 0 until TAPS) {
                sum += input[first + k] * table[row + k]
            }
            emit(sum)
            position += step
        }
        val drop = floor(position).toInt() - (HALF - 1)
        if (drop > 0) {
            System.arraycopy(input, drop, input, 0, inputCount - drop)
            inputCount -= drop
            position -= drop
        }
    }

    private fun emit(value: Float) {
        val sample = (value * gain).coerceIn(-32768f, 32767f).toInt().toShort()
        val loud = sample > SILENCE || sample < -SILENCE
        if (!loud) {
            if (quietCount == quiet.size) {
                if (!heardSound) {
                    // Before the first sound only the last [keep] samples matter.
                    System.arraycopy(quiet, quietCount - keep, quiet, 0, keep)
                    quietCount = keep
                } else {
                    quiet = quiet.copyOf(quiet.size * 2)
                }
            }
            quiet[quietCount++] = sample
            return
        }
        if (heardSound) {
            emitDirect(quiet, quietCount)
        } else {
            emitDirect(quiet, quietCount - min(quietCount, keep), min(quietCount, keep))
            heardSound = true
        }
        quietCount = 0
        if (outCount == out.size) flushOut()
        out[outCount++] = sample
    }

    private fun emitDirect(samples: ShortArray, count: Int) = emitDirect(samples, 0, count)

    private fun emitDirect(samples: ShortArray, from: Int, count: Int) {
        var i = from
        val end = from + count
        while (i < end) {
            if (outCount == out.size) flushOut()
            val n = min(out.size - outCount, end - i)
            System.arraycopy(samples, i, out, outCount, n)
            outCount += n
            i += n
        }
    }

    private fun flushOut(): Boolean {
        if (outCount > 0 && accepted) accepted = sink(out, outCount)
        outCount = 0
        return accepted
    }

    private companion object {
        /** Filter taps on each side of an output sample. */
        const val HALF = 8
        const val TAPS = HALF * 2
        const val PHASES = 256
        /** Passband edge as a share of the lower Nyquist frequency. */
        const val CUTOFF = 0.92
        /** Samples within this of zero count as silence, about -50 dB. */
        const val SILENCE: Short = 100
        const val KEEP_MS = 40

        /** Hann-windowed sinc coefficients, one row of [TAPS] per phase, each row summing to 1. */
        fun filterTable(cutoff: Double): FloatArray {
            val table = FloatArray(PHASES * TAPS)
            for (p in 0 until PHASES) {
                val frac = p.toDouble() / PHASES
                var sum = 0.0
                val row = DoubleArray(TAPS)
                for (k in 0 until TAPS) {
                    val d = (k - HALF + 1) - frac
                    val x = PI * cutoff * d
                    val sinc = if (d == 0.0) 1.0 else sin(x) / x
                    val window = if (kotlin.math.abs(d) >= HALF) 0.0 else 0.5 * (1 + cos(PI * d / HALF))
                    row[k] = cutoff * sinc * window
                    sum += row[k]
                }
                for (k in 0 until TAPS) table[p * TAPS + k] = (row[k] / sum).toFloat()
            }
            return table
        }
    }
}
