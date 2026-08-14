package com.seunghak.teslasing

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

data class VocalReading(val level: Float, val pitchHz: Float, val score: Int)

class VocalAnalyzer {
    @Volatile private var listening = false
    private var recorder: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start(targetNote: () -> Int, onReading: (VocalReading) -> Unit) {
        stop()
        listening = true
        thread(name = "tesing-vocal") {
            val sampleRate = 16_000
            val size = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)
            val audio = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size * 2
                )
            }.getOrNull() ?: return@thread
            recorder = audio
            val buffer = ShortArray(size)
            runCatching { audio.startRecording() }.onFailure { return@thread }
            while (listening) {
                val count = audio.read(buffer, 0, buffer.size)
                if (count <= 0) continue
                var energy = 0.0
                for (i in 0 until count) energy += buffer[i] * buffer[i].toDouble()
                val rms = sqrt(energy / count) / Short.MAX_VALUE
                val pitch = if (rms > .025) estimatePitch(buffer, count, sampleRate) else 0f
                val targetHz = midiToHz(targetNote())
                val cents = if (pitch > 0) abs(1200 * ln(pitch / targetHz) / ln(2.0)).toFloat() else 999f
                val score = if (rms <= .025 || pitch == 0f) 0 else (100 - cents / 5).toInt().coerceIn(20, 100)
                onReading(VocalReading((rms * 8).toFloat().coerceIn(0f, 1f), pitch, score))
            }
            runCatching { audio.stop() }
            audio.release()
            if (recorder === audio) recorder = null
        }
    }

    fun stop() {
        listening = false
        runCatching { recorder?.stop() }
        recorder = null
    }

    private fun estimatePitch(data: ShortArray, count: Int, sampleRate: Int): Float {
        var bestLag = 0
        var best = 0.0
        val minLag = sampleRate / 900
        val maxLag = sampleRate / 80
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            var i = 0
            while (i < count - lag) {
                correlation += data[i].toDouble() * data[i + lag]
                i += 2
            }
            if (correlation > best) {
                best = correlation
                bestLag = lag
            }
        }
        return if (bestLag == 0) 0f else sampleRate.toFloat() / bestLag
    }

    private fun midiToHz(note: Int) = (440.0 * Math.pow(2.0, (note - 69) / 12.0)).toFloat()
}
