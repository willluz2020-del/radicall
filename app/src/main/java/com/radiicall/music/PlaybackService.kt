package com.radiicall.music

import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null

    override fun onCreate() {
        super.onCreate()

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val sessionId = audioManager.generateAudioSessionId()

        val player = ExoPlayer.Builder(this).build().apply {
            setAudioSessionId(sessionId)
            playWhenReady = false
        }

        mediaSession = MediaSession.Builder(this, player).build()
        setupAudioEffects(sessionId)
        restoreAudioSettings()
    }

    private fun setupAudioEffects(sessionId: Int) {
        try {
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
        } catch (_: Exception) {
            bassBoost = null
        }

        try {
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
        } catch (_: Exception) {
            equalizer = null
        }
    }

    private fun restoreAudioSettings() {
        val prefs = getSharedPreferences("radiicall_audio", MODE_PRIVATE)
        applyBass(prefs.getInt("bass", 50))
        applyTreble(prefs.getInt("treble", 50))
    }

    private fun applyBass(value: Int) {
        val normalized = value.coerceIn(0, 100)
        val strength = (normalized * 10).coerceIn(0, 1000).toShort()
        try {
            bassBoost?.setStrength(strength)
        } catch (_: Exception) { }
        getSharedPreferences("radiicall_audio", MODE_PRIVATE)
            .edit().putInt("bass", normalized).apply()
    }

    private fun applyTreble(value: Int) {
        val eq = equalizer ?: return
        val normalized = value.coerceIn(0, 100)
        try {
            val min = eq.bandLevelRange[0].toInt()
            val max = eq.bandLevelRange[1].toInt()
            val level = when {
                normalized == 50 -> 0
                normalized < 50 -> (min * (50 - normalized) / 50.0).toInt()
                else -> (max * (normalized - 50) / 50.0).toInt()
            }.coerceIn(min, max).toShort()

            val bands = eq.numberOfBands.toInt()
            for (band in 0 until bands) {
                val centerHz = eq.getCenterFreq(band.toShort()) / 1000
                if (centerHz >= 4000) {
                    eq.setBandLevel(band.toShort(), level)
                }
            }
        } catch (_: Exception) { }
        getSharedPreferences("radiicall_audio", MODE_PRIVATE)
            .edit().putInt("treble", normalized).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_BASS -> applyBass(intent.getIntExtra(EXTRA_VALUE, 50))
            ACTION_SET_TREBLE -> applyTreble(intent.getIntExtra(EXTRA_VALUE, 50))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        try { bassBoost?.release() } catch (_: Exception) { }
        try { equalizer?.release() } catch (_: Exception) { }
        bassBoost = null
        equalizer = null

        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SET_BASS = "com.radiicall.music.SET_BASS"
        const val ACTION_SET_TREBLE = "com.radiicall.music.SET_TREBLE"
        const val EXTRA_VALUE = "value"
    }
}
