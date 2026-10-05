package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class SoundEffect {
    BUTTON,
    DICE_ROLL,
    TOKEN_MOVE,
    CAPTURE,
    HOME_REACHED,
    VICTORY,
    TURN_NOTIFY
}

class SoundAndHapticsManager(private val context: Context) {
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun playEffect(
        effect: SoundEffect,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) {
        if (vibrationEnabled) {
            triggerHaptic(effect)
        }
        if (soundEnabled) {
            audioScope.launch {
                runCatching {
                    when (effect) {
                        SoundEffect.BUTTON -> playToneSequence(listOf(660.0 to 35))
                        SoundEffect.DICE_ROLL -> playToneSequence(
                            listOf(320.0 to 30, 440.0 to 30, 560.0 to 35, 680.0 to 45)
                        )
                        SoundEffect.TOKEN_MOVE -> playToneSequence(listOf(523.25 to 45, 659.25 to 55))
                        SoundEffect.CAPTURE -> playToneSequence(
                            listOf(784.0 to 65, 587.33 to 65, 880.0 to 120)
                        )
                        SoundEffect.HOME_REACHED -> playToneSequence(
                            listOf(523.25 to 60, 659.25 to 60, 783.99 to 80, 1046.50 to 140)
                        )
                        SoundEffect.VICTORY -> playToneSequence(
                            listOf(523.25 to 90, 659.25 to 90, 783.99 to 90, 1046.50 to 220)
                        )
                        SoundEffect.TURN_NOTIFY -> playToneSequence(listOf(440.0 to 50, 554.37 to 70))
                    }
                }
            }
        }
    }

    private fun triggerHaptic(effect: SoundEffect) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                val durationMs = when (effect) {
                    SoundEffect.BUTTON -> 15L
                    SoundEffect.DICE_ROLL -> 40L
                    SoundEffect.TOKEN_MOVE -> 20L
                    SoundEffect.CAPTURE -> 85L
                    SoundEffect.HOME_REACHED -> 65L
                    SoundEffect.VICTORY -> 140L
                    SoundEffect.TURN_NOTIFY -> 30L
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        }
    }

    private fun playToneSequence(notes: List<Pair<Double, Int>>) {
        val sampleRate = 22050
        val totalMs = notes.sumOf { it.second }.coerceAtLeast(20)
        val totalSamples = (sampleRate * totalMs) / 1000
        val pcm = ShortArray(totalSamples)
        var offset = 0

        for ((freq, durationMs) in notes) {
            val count = ((sampleRate * durationMs) / 1000).coerceAtMost(totalSamples - offset)
            for (i in 0 until count) {
                val envelope = 1.0 - (i.toDouble() / count.toDouble()) * 0.65
                val sample = sin(2.0 * PI * i * freq / sampleRate) * envelope
                pcm[offset + i] = (sample * Short.MAX_VALUE * 0.25).toInt().toShort()
            }
            offset += count
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep(totalMs.toLong() + 20L)
        track.stop()
        track.release()
    }
}
