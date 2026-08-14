package com.seunghak.teslasing

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Lightweight original accompaniment so the sample app ships without copyrighted audio. */
class SynthEngine {
    @Volatile private var playing = false
    private var audioTrack: AudioTrack? = null
    private var worker: Thread? = null

    fun play(song: Song, keyShift: Int, tempo: Float, fromMs: Long = 0) {
        stop()
        playing = true
        worker = thread(name = "tesing-synth") {
            val sampleRate = 22_050
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = track
            track.play()

            val frames = 1024
            val buffer = ShortArray(frames * 2)
            var framePosition = (fromMs * sampleRate / 1000.0).toLong()
            val beatSeconds = 60.0 / (song.bpm * tempo)
            val totalFrames = (song.durationMs / tempo * sampleRate / 1000.0).toLong()
            while (playing && framePosition < totalFrames) {
                for (i in 0 until frames) {
                    val time = (framePosition + i) / sampleRate.toDouble()
                    val beat = (time / beatSeconds).toInt()
                    val note = song.melody[(beat / 2) % song.melody.size] + keyShift
                    val root = intArrayOf(48, 45, 53, 55)[(beat / 8) % 4] + keyShift
                    val leadHz = 440.0 * 2.0.pow((note - 69) / 12.0)
                    val bassHz = 440.0 * 2.0.pow((root - 69) / 12.0)
                    val beatPhase = (time % beatSeconds) / beatSeconds
                    val pluck = (1.0 - beatPhase).pow(3)
                    val kick = if (beatPhase < .12) sin(2 * PI * (68 - 35 * beatPhase) * time) * (1 - beatPhase / .12) else 0.0
                    val chord = sin(2 * PI * bassHz * time) + .45 * sin(2 * PI * bassHz * 1.5 * time)
                    val lead = sin(2 * PI * leadHz * time) * pluck
                    val sample = ((chord * .18 + lead * .20 + kick * .26) * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    buffer[i * 2] = sample
                    buffer[i * 2 + 1] = sample
                }
                track.write(buffer, 0, buffer.size)
                framePosition += frames
            }
            runCatching { track.stop() }
            track.release()
            if (audioTrack === track) audioTrack = null
        }
    }

    fun stop() {
        playing = false
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        worker?.interrupt()
        worker = null
        audioTrack = null
    }
}
