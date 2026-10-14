package com.kirankotla.connectfour.util

import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Policy defining whether background music audio should be playing.
 * Audio is strictly forbidden when the app is not in the foreground.
 */
object MusicPlaybackPolicy {
    fun shouldPlayAudio(
        isForeground: Boolean,
        isMusicEnabled: Boolean,
        shouldPlay: Boolean
    ): Boolean = isForeground && isMusicEnabled && shouldPlay
}

/**
 * Background Music (BGM) Manager for Connect Four.
 * Plays a soothing, procedurally-synthesized ambient music loop.
 * Battery-friendly, strictly lifecycle-aware, and responsive to user preferences.
 * Ensures audio is always paused whenever the app is not in the foreground.
 */
class MusicManager(private val context: Context) : DefaultLifecycleObserver {

    private val TAG = "MusicManager"
    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared: Boolean = false
    private var shouldPlay: Boolean = false
    private var isForeground: Boolean = false
    private var hasAudioFocus: Boolean = false
    private var isDucked: Boolean = false
    private var currentVolume: Float = 0.14f

    var isMusicEnabled: Boolean = true
        set(value) {
            field = value
            updatePlayback()
        }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                synchronized(this@MusicManager) {
                    hasAudioFocus = false
                    updatePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                synchronized(this@MusicManager) {
                    isDucked = true
                    applyVolume()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                synchronized(this@MusicManager) {
                    hasAudioFocus = true
                    isDucked = false
                    applyVolume()
                    updatePlayback()
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            initPlayer()
        }
    }

    private fun initPlayer() {
        try {
            val musicDir = File(context.cacheDir, "connectfour_music").apply { mkdirs() }
            val bgmFile = File(musicDir, "connectfour_arcade_strategy_bgm_v3.wav")
            generateArcadeStrategyLoopWav(bgmFile)

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(bgmFile.absolutePath)
                isLooping = true
                setVolume(currentVolume, currentVolume)
                setOnPreparedListener {
                    synchronized(this@MusicManager) {
                        isPrepared = true
                        updatePlayback()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    synchronized(this@MusicManager) {
                        isPrepared = false
                    }
                    true
                }
                prepareAsync()
            }
            synchronized(this) {
                mediaPlayer = player
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize background music player: ${e.message}")
        }
    }

    val isAppInForeground: Boolean
        get() = isForeground

    val isPlayRequested: Boolean
        get() = shouldPlay

    val canPlay: Boolean
        get() = MusicPlaybackPolicy.shouldPlayAudio(
            isForeground = isForeground,
            isMusicEnabled = isMusicEnabled,
            shouldPlay = shouldPlay
        )

    @Synchronized
    private fun updatePlayback() {
        val player = mediaPlayer ?: return
        if (!isPrepared) return

        try {
            if (canPlay) {
                if (!hasAudioFocus) {
                    if (!requestAudioFocusInternal()) {
                        Log.d(TAG, "Audio focus not granted; deferring playback")
                        return
                    }
                }
                applyVolume()
                if (!player.isPlaying) {
                    player.start()
                }
            } else {
                if (player.isPlaying) {
                    player.pause()
                }
                if (hasAudioFocus) {
                    abandonAudioFocusInternal()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in updatePlayback: ${e.message}")
        }
    }

    private fun applyVolume() {
        val vol = if (isDucked) (currentVolume * 0.25f) else currentVolume
        try {
            mediaPlayer?.setVolume(vol, vol)
        } catch (_: Exception) {}
    }

    private fun requestAudioFocusInternal(): Boolean {
        val am = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestAudioFocusOreo(am)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun requestAudioFocusOreo(am: AudioManager): Int {
        val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(afChangeListener)
            .setAcceptsDelayedFocusGain(false)
            .build().also { audioFocusRequest = it }
        return am.requestAudioFocus(request)
    }

    private fun abandonAudioFocusInternal() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                abandonAudioFocusOreo(am)
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(afChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus: ${e.message}")
        }
        hasAudioFocus = false
        isDucked = false
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun abandonAudioFocusOreo(am: AudioManager) {
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    fun start() {
        synchronized(this) {
            shouldPlay = true
            updatePlayback()
        }
    }

    fun pause() {
        synchronized(this) {
            shouldPlay = false
            updatePlayback()
        }
    }

    fun resume() {
        synchronized(this) {
            shouldPlay = true
            updatePlayback()
        }
    }

    fun stop() {
        synchronized(this) {
            shouldPlay = false
            updatePlayback()
        }
    }

    fun onAppForeground() {
        synchronized(this) {
            if (!isForeground) {
                isForeground = true
                updatePlayback()
            }
        }
    }

    fun onAppBackground() {
        synchronized(this) {
            if (isForeground) {
                isForeground = false
                updatePlayback()
            }
        }
    }

    // DefaultLifecycleObserver overrides
    override fun onResume(owner: LifecycleOwner) {
        onAppForeground()
    }

    override fun onPause(owner: LifecycleOwner) {
        onAppBackground()
    }

    override fun onStop(owner: LifecycleOwner) {
        onAppBackground()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
    }

    fun setVolume(volume: Float) {
        synchronized(this) {
            currentVolume = volume.coerceIn(0f, 1f)
            applyVolume()
        }
    }

    fun release() {
        synchronized(this) {
            shouldPlay = false
            isForeground = false
            abandonAudioFocusInternal()
            try {
                mediaPlayer?.run {
                    if (isPlaying) stop()
                    release()
                }
            } catch (_: Exception) {}
            mediaPlayer = null
            isPrepared = false
        }
        scope.cancel()
    }

    companion object {
        /** Generates an original arcade-strategy loop with a restrained pulse and arpeggio. */
        private fun generateArcadeStrategyLoopWav(targetFile: File, sampleRate: Int = 22050, durationSec: Double = 8.0) {
            if (targetFile.exists() && targetFile.length() > 44) return

            val totalSamples = (sampleRate * durationSec).toInt()
            val dataSize = totalSamples * 2
            val totalFileSize = 36 + dataSize

            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put('R'.code.toByte()); put('I'.code.toByte()); put('F'.code.toByte()); put('F'.code.toByte())
                putInt(totalFileSize)
                put('W'.code.toByte()); put('A'.code.toByte()); put('V'.code.toByte()); put('E'.code.toByte())
                put('f'.code.toByte()); put('m'.code.toByte()); put('t'.code.toByte()); put(' '.code.toByte())
                putInt(16)
                putShort(1.toShort()) // PCM
                putShort(1.toShort()) // Mono
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2.toShort())
                putShort(16.toShort())
                put('d'.code.toByte()); put('a'.code.toByte()); put('t'.code.toByte()); put('a'.code.toByte())
                putInt(dataSize)
            }

            val pcmBuffer = ByteBuffer.allocate(dataSize).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                val chords = listOf(
                    listOf(130.81, 261.63, 329.63, 392.00), // Cmaj7
                    listOf(146.83, 293.66, 349.23, 440.00), // Dm7
                    listOf(110.00, 220.00, 261.63, 329.63), // Am7
                    listOf(98.00, 196.00, 246.94, 392.00)   // G6
                )
                val barDurationSec = 2.0

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate.toDouble()
                    val barIndex = ((t / barDurationSec).toInt()).coerceIn(0, 3)
                    val localT = t % barDurationSec
                    val chord = chords[barIndex]

                    // Soft chord bed under a 120 BPM pulse.
                    val attack = (localT / 0.12).coerceIn(0.0, 1.0)
                    val decay = exp(-localT * 1.45)
                    val envelope = attack * decay

                    var sample = 0.0
                    for ((noteIdx, freq) in chord.withIndex()) {
                        val phase = 2.0 * PI * freq * t
                        val weight = when (noteIdx) {
                            0 -> 0.38
                            1 -> 0.28
                            2 -> 0.22
                            else -> 0.16
                        }
                        val wave = sin(phase) + 0.18 * sin(phase * 2.0)
                        sample += wave * weight
                    }

                    val beatPhase = t % 0.5
                    val beatEnvelope = exp(-beatPhase * 24.0)
                    val pulse = sin(2.0 * PI * 65.41 * t) * beatEnvelope * 0.20
                    val step = ((localT / 0.5).toInt()).coerceIn(0, 3)
                    val arp = sin(2.0 * PI * chord[step] * 2.0 * t) * exp(-beatPhase * 8.0) * 0.12
                    val sampleVal = ((sample * envelope * 0.34) + pulse + arp) .times(Short.MAX_VALUE).toInt()
                    putShort(sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                }
            }

            FileOutputStream(targetFile).use { fos ->
                fos.write(header.array())
                fos.write(pcmBuffer.array())
                fos.flush()
            }
        }
    }
}
