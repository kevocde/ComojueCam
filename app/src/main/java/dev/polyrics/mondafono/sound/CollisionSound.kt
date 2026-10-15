package dev.polyrics.mondafono.sound

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class CollisionSound {
    private val handler = Handler(Looper.getMainLooper())
    private var generator: ToneGenerator? = null
    private var toneIndex = 0
    private var stopAt = 0L
    private val tones = intArrayOf(
        ToneGenerator.TONE_PROP_BEEP2,
        ToneGenerator.TONE_PROP_BEEP
    )
    private val toneLoop = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() >= stopAt) {
                stop()
                return
            }
            generator?.startTone(tones[toneIndex++ % tones.size], TONE_DURATION_MS.toInt())
            handler.postDelayed(this, TONE_INTERVAL_MS)
        }
    }

    fun play() {
        handler.post {
            stop()
            generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            toneIndex = 0
            stopAt = SystemClock.elapsedRealtime() + SOUND_DURATION_MS
            handler.post(toneLoop)
        }
    }

    fun release() {
        stop()
        handler.removeCallbacksAndMessages(null)
    }

    private fun stop() {
        handler.removeCallbacks(toneLoop)
        generator?.release()
        generator = null
    }

    private companion object {
        const val SOUND_DURATION_MS = 5_000L
        const val TONE_DURATION_MS = 240L
        const val TONE_INTERVAL_MS = 320L
        const val VOLUME = 100
    }
}
