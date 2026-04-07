package com.oro.hearingboost

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HearingBoost Audio Processor
 *
 * Pipeline:
 *  AudioRecord (mono) → Voice EQ (band-pass 300–3400 Hz) →
 *  Noise Gate → Gain L/R (0–300%) → AudioTrack (stereo)
 *
 * Noise reduction is implemented as a spectral gate:
 * a first-order IIR high-pass + adaptive RMS gate.
 */
class AudioProcessor {

    // ── Config ──────────────────────────────────────────────────────────────
    companion object {
        const val SAMPLE_RATE      = 44100
        const val CHANNEL_IN       = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT      = AudioFormat.CHANNEL_OUT_STEREO
        const val ENCODING         = AudioFormat.ENCODING_PCM_FLOAT
        const val BUFFER_FRAMES    = 512
        const val MAX_GAIN         = 3.0f   // 300 %

        // Voice-frequency band (Hz)
        const val VOICE_LOW_HZ     = 300.0
        const val VOICE_HIGH_HZ    = 3400.0

        // Noise gate: signal below this fraction of mean RMS is attenuated
        const val GATE_RATIO       = 0.15f
    }

    // ── State ────────────────────────────────────────────────────────────────
    @Volatile var gainLeft   = 1.0f   // 0.0 – 3.0
    @Volatile var gainRight  = 1.0f
    @Volatile var noiseLevel = 0.5f   // 0.0 = off, 1.0 = max

    // ── Internal ─────────────────────────────────────────────────────────────
    private var record    : AudioRecord? = null
    private var track     : AudioTrack?  = null
    private var noiseSup  : NoiseSuppressor? = null
    private var job       : Job? = null
    private val scope     = CoroutineScope(Dispatchers.Default)

    // Biquad coefficients (updated when params change)
    private val bpLow  = BiquadFilter()   // high-pass  @ VOICE_LOW_HZ
    private val bpHigh = BiquadFilter()   // low-pass   @ VOICE_HIGH_HZ

    init {
        bpLow.setHighPass(VOICE_LOW_HZ,  SAMPLE_RATE.toDouble(), 0.707)
        bpHigh.setLowPass(VOICE_HIGH_HZ, SAMPLE_RATE.toDouble(), 0.707)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun start() {
        if (job?.isActive == true) return

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufSize = max(minBuf, BUFFER_FRAMES * 4)

        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_IN, ENCODING, bufSize
        )

        val trackBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(trackBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // Hardware noise suppressor if available (best-effort)
        val rid = record!!.audioSessionId
        if (NoiseSuppressor.isAvailable()) {
            noiseSup = NoiseSuppressor.create(rid)
        }

        record!!.startRecording()
        track!!.play()

        job = scope.launch {
            val inBuf  = FloatArray(BUFFER_FRAMES)
            val outBuf = FloatArray(BUFFER_FRAMES * 2) // interleaved stereo

            // Adaptive RMS tracker
            var smoothRms = 0.0f

            while (isActive) {
                val read = record!!.read(inBuf, 0, BUFFER_FRAMES, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                // ── 1. Voice EQ (band-pass) ──────────────────────────────
                bpLow.reset()
                bpHigh.reset()
                for (i in 0 until read) {
                    var s = bpLow.process(inBuf[i].toDouble())
                    s = bpHigh.process(s)
                    inBuf[i] = s.toFloat()
                }

                // ── 2. Compute RMS ───────────────────────────────────────
                var rms = 0.0f
                for (i in 0 until read) rms += inBuf[i] * inBuf[i]
                rms = sqrt(rms / read)
                smoothRms = 0.95f * smoothRms + 0.05f * rms

                // ── 3. Noise gate (software) ─────────────────────────────
                // noiseLevel 0 = gate disabled, 1 = aggressive gate
                val threshold = smoothRms * GATE_RATIO * noiseLevel * 4f
                val gateAlpha = noiseLevel   // blend: 0 = raw, 1 = gated

                for (i in 0 until read) {
                    val amp   = Math.abs(inBuf[i])
                    val gated = if (amp < threshold) inBuf[i] * 0.05f else inBuf[i]
                    inBuf[i]  = inBuf[i] * (1f - gateAlpha) + gated * gateAlpha
                }

                // ── 4. Apply separate L/R gain & hard-clip ───────────────
                val gl = min(gainLeft,  MAX_GAIN)
                val gr = min(gainRight, MAX_GAIN)

                var j = 0
                for (i in 0 until read) {
                    outBuf[j++] = clip(inBuf[i] * gl)
                    outBuf[j++] = clip(inBuf[i] * gr)
                }

                track!!.write(outBuf, 0, read * 2, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { record?.stop(); record?.release() }
        runCatching { track?.stop();  track?.release() }
        runCatching { noiseSup?.release() }
        record   = null
        track    = null
        noiseSup = null
        bpLow.reset()
        bpHigh.reset()
    }

    val isRunning get() = job?.isActive == true

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun clip(v: Float) = v.coerceIn(-1f, 1f)

    // ── Biquad IIR filter ─────────────────────────────────────────────────────

    private class BiquadFilter {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
        private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        fun setLowPass(freq: Double, sr: Double, q: Double) {
            val w0 = 2.0 * PI * freq / sr
            val alpha = sin(w0) / (2.0 * q)
            val cosw0 = cos(w0)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosw0) / 2.0) / a0
            b1 = (1.0 - cosw0) / a0
            b2 = ((1.0 - cosw0) / 2.0) / a0
            a1 = (-2.0 * cosw0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun setHighPass(freq: Double, sr: Double, q: Double) {
            val w0 = 2.0 * PI * freq / sr
            val alpha = sin(w0) / (2.0 * q)
            val cosw0 = cos(w0)
            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosw0) / 2.0) / a0
            b1 = (-(1.0 + cosw0)) / a0
            b2 = ((1.0 + cosw0) / 2.0) / a0
            a1 = (-2.0 * cosw0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }

        fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }
    }
}
