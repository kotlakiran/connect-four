package com.kirankotla.connectfour.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sound effect manager powered by Android's SoundPool for ultra-low latency
 * audio feedback on grid captures, turn changes, game wins, and board moves.
 * Includes synthetic PCM/WAV generation and a ToneGenerator fallback.
 */
class SoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var toneGenerator: ToneGenerator? = null
    var isEnabled: Boolean = true

    // SoundPool sound IDs
    private var soundGridCapture: Int = 0
    private var soundDoubleBox: Int = 0
    private var soundTurnChange: Int = 0
    private var soundGameWin: Int = 0
    private var soundLineStroke: Int = 0
    private var soundDiceRoll: Int = 0
    private var soundTimerTick: Int = 0
    private var soundTimeWarning: Int = 0
    private var soundDragTick: Int = 0
    private var soundUiTap: Int = 0
    private var soundMatchPing: Int = 0
    private var soundPlayerSearch: Int = 0
    private var soundMatchFound: Int = 0
    private var playerSearchStreamId: Int = 0
    private var masterVolume: Float = 1.0f

    private val readySoundIds = mutableSetOf<Int>()
    private val rewardSoundIds = mutableMapOf<String, Int>()

    init {
        initSoundPool()
        initToneFallback()
    }

    private fun initToneFallback() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    private fun initSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(audioAttributes)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    readySoundIds.add(sampleId)
                }
            }
            soundPool = pool

            // Prepare audio directory and synthesize audio files
            val soundDir = File(context.cacheDir, "connectfour_sound_effects").apply { mkdirs() }

            val captureFile = getOrSynthesizeWav(File(soundDir, "grid_capture.wav")) { t, total ->
                // Sweet chime chord (C6 1046.5Hz + E6 1318.5Hz) with sparkling decay
                val timeSec = t.toDouble() / 44100.0
                val decay = exp(-timeSec * 16.0)
                val s1 = sin(2.0 * PI * 1046.5 * timeSec)
                val s2 = sin(2.0 * PI * 1318.5 * timeSec) * 0.7
                val s3 = sin(2.0 * PI * 2093.0 * timeSec) * 0.35
                ((s1 + s2 + s3) * decay * 0.45 * Short.MAX_VALUE).toInt().toShort()
            }

            val doubleBoxFile = getOrSynthesizeWav(File(soundDir, "double_box.wav"), durationSec = 0.32) { t, total ->
                // Ascending triumphant 3-tone flourish (G5 784Hz -> C6 1046Hz -> E6 1318Hz)
                val timeSec = t.toDouble() / 44100.0
                val segment = (timeSec / 0.10).toInt()
                val freq = when (segment) {
                    0 -> 783.99
                    1 -> 1046.50
                    else -> 1318.51
                }
                val localTime = timeSec - (segment * 0.10)
                val decay = exp(-localTime * 12.0)
                val wave = sin(2.0 * PI * freq * timeSec) + 0.3 * sin(4.0 * PI * freq * timeSec)
                (wave * decay * 0.45 * Short.MAX_VALUE).toInt().toShort()
            }

            val turnChangeFile = getOrSynthesizeWav(File(soundDir, "turn_change.wav"), durationSec = 0.14) { t, total ->
                // Clean acoustic wooden click-clack cue
                val timeSec = t.toDouble() / 44100.0
                val freq = if (timeSec < 0.06) 620.0 else 480.0
                val localT = if (timeSec < 0.06) timeSec else timeSec - 0.06
                val decay = exp(-localT * 40.0)
                (sin(2.0 * PI * freq * timeSec) * decay * 0.4 * Short.MAX_VALUE).toInt().toShort()
            }

            val gameWinFile = getOrSynthesizeWav(File(soundDir, "game_win.wav"), durationSec = 0.65) { t, total ->
                // Ascending victorious celebration fanfare (C5 -> E5 -> G5 -> C6 chime)
                val timeSec = t.toDouble() / 44100.0
                val step = (timeSec / 0.14).toInt()
                val freq = when (step) {
                    0 -> 523.25
                    1 -> 659.25
                    2 -> 783.99
                    else -> 1046.50
                }
                val localT = if (step >= 3) timeSec - 0.42 else timeSec - (step * 0.14)
                val decay = if (step >= 3) exp(-localT * 4.5) else exp(-localT * 10.0)
                val fundamental = sin(2.0 * PI * freq * timeSec)
                val overtone = sin(4.0 * PI * freq * timeSec) * 0.25
                ((fundamental + overtone) * decay * 0.45 * Short.MAX_VALUE).toInt().toShort()
            }

            val lineStrokeFile = getOrSynthesizeWav(File(soundDir, "line_stroke.wav"), durationSec = 0.055) { t, _ ->
                // Crisp acrylic/wood chip drop click with solid tactile snap
                val timeSec = t.toDouble() / 44100.0
                val decay = exp(-timeSec * 70.0)
                val fundamental = sin(2.0 * PI * 580.0 * timeSec) * 0.7
                val overtone = sin(2.0 * PI * 1160.0 * timeSec) * 0.3
                ((fundamental + overtone) * decay * 0.45 * Short.MAX_VALUE).toInt().toShort()
            }

            val diceRollFile = getOrSynthesizeWav(File(soundDir, "dice_roll.wav"), durationSec = 0.26) { t, _ ->
                // Rattling tumble pulses
                val timeSec = t.toDouble() / 44100.0
                val phase = (timeSec * 45.0).toInt() % 2
                val decay = exp(-timeSec * 7.0)
                val freq = 400.0 + (phase * 180.0)
                (sin(2.0 * PI * freq * timeSec) * decay * 0.3 * Short.MAX_VALUE).toInt().toShort()
            }

            val timerTickFile = getOrSynthesizeWav(File(soundDir, "timer_tick.wav"), durationSec = 0.035) { t, _ ->
                // Subtle clock tick
                val timeSec = t.toDouble() / 44100.0
                val decay = exp(-timeSec * 90.0)
                (sin(2.0 * PI * 1150.0 * timeSec) * decay * 0.25 * Short.MAX_VALUE).toInt().toShort()
            }

            val timeWarningFile = getOrSynthesizeWav(File(soundDir, "time_warning.wav"), durationSec = 0.12) { t, _ ->
                // Alerting urgent pip
                val timeSec = t.toDouble() / 44100.0
                val decay = exp(-timeSec * 25.0)
                (sin(2.0 * PI * 920.0 * timeSec) * decay * 0.45 * Short.MAX_VALUE).toInt().toShort()
            }

            val dragTickFile = getOrSynthesizeWav(File(soundDir, "drag_tick.wav"), durationSec = 0.02) { t, _ ->
                // Ultra-short gentle wood tick for drag hover
                val timeSec = t.toDouble() / 44100.0
                val decay = exp(-timeSec * 160.0)
                (sin(2.0 * PI * 1320.0 * timeSec) * decay * 0.3 * Short.MAX_VALUE).toInt().toShort()
            }

            val uiTapFile = getOrSynthesizeWav(File(soundDir, "ui_tap.wav"), durationSec = 0.035) { t, _ ->
                // Clean button pop
                val timeSec = t.toDouble() / 44100.0
                val freq = 560.0 - (timeSec * 3200.0)
                val decay = exp(-timeSec * 95.0)
                (sin(2.0 * PI * freq * timeSec) * decay * 0.35 * Short.MAX_VALUE).toInt().toShort()
            }

            val playerSearchFile = getOrSynthesizeWav(File(soundDir, "player_search_chime_v2.wav"), durationSec = 0.30) { t, _ ->
                // Distinct three-step profile-search melody: warm, light, and non-urgent
                val timeSec = t.toDouble() / 44100.0
                val step = (timeSec / 0.10).toInt().coerceAtMost(2)
                val freq = when (step) {
                    0 -> 392.0
                    1 -> 523.25
                    else -> 659.25
                }
                val localTime = timeSec - step * 0.10
                val decay = exp(-localTime * 18.0)
                val wave = sin(2.0 * PI * freq * timeSec) + 0.18 * sin(2.0 * PI * freq * 2.0 * timeSec)
                (wave * decay * 0.20 * Short.MAX_VALUE).toInt().toShort()
            }

            val matchFoundFile = getOrSynthesizeWav(File(soundDir, "match_found.wav"), durationSec = 0.48) { t, _ ->
                // Dual-tone high-tech triumphant matchmaking locked sound (D5 587Hz -> A5 880Hz -> D6 1175Hz)
                val timeSec = t.toDouble() / 44100.0
                val step = (timeSec / 0.14).toInt()
                val freq = when (step) {
                    0 -> 587.33
                    1 -> 880.00
                    else -> 1174.66
                }
                val localT = timeSec - (step.coerceAtMost(2) * 0.14)
                val decay = exp(-localT * 8.0)
                val wave = sin(2.0 * PI * freq * timeSec) + 0.3 * sin(2.0 * PI * freq * 2.0 * timeSec)
                (wave * decay * 0.45 * Short.MAX_VALUE).toInt().toShort()
            }

            // Load samples into SoundPool
            soundGridCapture = pool.load(captureFile.absolutePath, 1)
            soundDoubleBox = pool.load(doubleBoxFile.absolutePath, 1)
            soundTurnChange = pool.load(turnChangeFile.absolutePath, 1)
            soundGameWin = pool.load(gameWinFile.absolutePath, 1)
            soundLineStroke = pool.load(lineStrokeFile.absolutePath, 1)
            soundDiceRoll = pool.load(diceRollFile.absolutePath, 1)
            soundTimerTick = pool.load(timerTickFile.absolutePath, 1)
            soundTimeWarning = pool.load(timeWarningFile.absolutePath, 1)
            soundDragTick = pool.load(dragTickFile.absolutePath, 1)
            soundUiTap = pool.load(uiTapFile.absolutePath, 1)
            soundMatchPing = pool.load(playerSearchFile.absolutePath, 1)
            soundPlayerSearch = pool.load(playerSearchFile.absolutePath, 1)
            soundMatchFound = pool.load(matchFoundFile.absolutePath, 1)

            for ((family, duration) in REWARD_FAMILIES) {
                val file = getOrSynthesizeWav(File(soundDir, "reward_v1_$family.wav"), durationSec = duration) { t, _ ->
                    val timeSec = t.toDouble() / 44100.0
                    (rewardWave(family, timeSec) * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                }
                rewardSoundIds[family] = pool.load(file.absolutePath, 1)
            }

        } catch (e: Exception) {
            Log.w("SoundManager", "SoundPool init exception, using fallback tone generator", e)
        }
    }

    /**
     * Play audio feedback when a player captures a box/square on the grid.
     */
    fun playGridCapture() {
        if (!isEnabled) return
        if (!playSample(soundGridCapture, volume = 0.95f)) {
            fallbackTone(ToneGenerator.TONE_PROP_ACK, 80)
        }
    }

    /**
     * Alias for playGridCapture to ensure backwards-compatibility.
     */
    fun playBoxClaim() {
        playGridCapture()
    }

    /**
     * Play triumphant flourish when multiple boxes are captured simultaneously in a single stroke.
     */
    fun playDoubleBoxFlourish() {
        if (!isEnabled) return
        if (!playSample(soundDoubleBox, volume = 1.0f)) {
            fallbackTone(ToneGenerator.TONE_DTMF_A, 120)
        }
    }

    /**
     * Play audio feedback when the active turn transitions between players or AI.
     */
    fun playTurnChange() {
        if (!isEnabled) return
        if (!playSample(soundTurnChange, volume = 0.85f)) {
            fallbackTone(ToneGenerator.TONE_PROP_BEEP2, 45)
        }
    }

    /**
     * Play celebration fanfare when a game is won and the match completes.
     */
    fun playGameWin() {
        if (!isEnabled) return
        if (!playSample(soundGameWin, volume = 1.0f)) {
            fallbackTone(ToneGenerator.TONE_DTMF_0, 220)
        }
    }

    /**
     * Alias for playGameWin to ensure backwards-compatibility.
     */
    fun playVictory() {
        playGameWin()
    }

    /**
     * Play tactile connection snap when a line segment is drawn.
     */
    fun playLineStroke() {
        if (!isEnabled) return
        if (!playSample(soundLineStroke, volume = 0.75f)) {
            fallbackTone(ToneGenerator.TONE_PROP_BEEP, 35)
        }
    }

    /**
     * Play rising pitch flourish when a player chains consecutive box captures.
     */
    fun playChainCombo(comboCount: Int) {
        if (!isEnabled) return
        val tone = when {
            comboCount >= 4 -> ToneGenerator.TONE_DTMF_D
            comboCount == 3 -> ToneGenerator.TONE_DTMF_C
            comboCount == 2 -> ToneGenerator.TONE_DTMF_B
            else -> ToneGenerator.TONE_PROP_ACK
        }
        if (!playSample(soundDoubleBox, volume = minOf(1.0f, 0.8f + comboCount * 0.08f))) {
            fallbackTone(tone, 100 + comboCount * 20)
        }
    }

    /**
     * Play subtle cautionary tone when player prepares a dangerous move.
     */
    fun playDangerWarning() {
        if (!isEnabled) return
        fallbackTone(ToneGenerator.TONE_PROP_NACK, 50)
    }

    /**
     * Play dice tumbling sound in Dice Mode.
     */
    fun playDiceRoll() {
        if (!isEnabled) return
        if (!playSample(soundDiceRoll, volume = 0.85f)) {
            fallbackTone(ToneGenerator.TONE_PROP_PROMPT, 60)
        }
    }

    /**
     * Play seconds countdown clock tick.
     */
    fun playTimerTick() {
        if (!isEnabled) return
        if (!playSample(soundTimerTick, volume = 0.5f)) {
            fallbackTone(ToneGenerator.TONE_PROP_BEEP2, 30)
        }
    }

    /**
     * Play urgent speed warning tick.
     */
    fun playTimeWarning() {
        if (!isEnabled) return
        if (!playSample(soundTimeWarning, volume = 0.9f)) {
            fallbackTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 80)
        }
    }

    /**
     * Play ultra-short subtle tactile audio tick when scrubbing / dragging across edges.
     */
    fun playDragHover() {
        if (!isEnabled) return
        if (!playSample(soundDragTick, volume = 0.45f)) {
            fallbackTone(ToneGenerator.TONE_PROP_BEEP2, 12)
        }
    }

    /**
     * Play satisfying, crisp button pop for general UI taps and navigation.
     */
    fun playUiTap() {
        if (!isEnabled) return
        if (!playSample(soundUiTap, volume = 0.65f)) {
            fallbackTone(ToneGenerator.TONE_PROP_BEEP, 18)
        }
    }

    /**
     * Play futuristic radar/sonar ping during matchmaking searches.
     */
    fun playMatchmakingSearch() {
        if (!isEnabled) return
        if (!playSample(soundMatchPing, volume = 0.85f)) {
            fallbackTone(ToneGenerator.TONE_PROP_BEEP2, 50)
        }
    }

    /** Play a softer radar pulse while looking for another player. */
    fun playPlayerSearchPulse() {
        if (!isEnabled) return
        if (!playSearchSample(volume = 0.55f)) {
            fallbackTone(ToneGenerator.TONE_PROP_BEEP2, 35)
        }
    }

    /** Play a quiet confirmation when the visible search profile changes. */
    fun playProfileSearchChange() {
        if (!isEnabled) return
        if (!playSearchSample(volume = 0.32f)) {
            fallbackTone(ToneGenerator.TONE_PROP_BEEP2, 22)
        }
    }

    fun stopPlayerSearchSound() {
        try {
            if (playerSearchStreamId != 0) {
                soundPool?.stop(playerSearchStreamId)
                playerSearchStreamId = 0
            }
        } catch (_: Exception) {}
    }

    /**
     * Play lock-in chime when an online opponent connects.
     */
    fun playMatchFound() {
        if (!isEnabled) return
        if (!playSample(soundMatchFound, volume = 1.0f)) {
            fallbackTone(ToneGenerator.TONE_DTMF_D, 180)
        }
    }

    /** Play the distinct sound mapped to a reward [profile]; null or unknown plays a coin cling. */
    fun playRewardSound(profile: String?) {
        if (!isEnabled) return
        val family = REWARD_PROFILE_FAMILY[profile] ?: "cling"
        val id = rewardSoundIds[family] ?: 0
        val volume = if (family == "fanfare" || family == "burst") 1.0f else 0.85f
        if (!playSample(id, volume)) {
            fallbackTone(ToneGenerator.TONE_PROP_ACK, 80)
        }
    }

    fun setVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
    }

    private fun playSample(soundId: Int, volume: Float): Boolean {
        val pool = soundPool ?: return false
        if (soundId == 0 || !readySoundIds.contains(soundId)) return false
        val finalVol = (volume * masterVolume).coerceIn(0f, 1f)
        return try {
            val streamId = pool.play(soundId, finalVol, finalVol, 1, 0, 1.0f)
            streamId != 0
        } catch (_: Exception) {
            false
        }
    }

    private fun playSearchSample(volume: Float): Boolean {
        val pool = soundPool ?: return false
        if (soundPlayerSearch == 0 || !readySoundIds.contains(soundPlayerSearch)) return false
        stopPlayerSearchSound()
        val finalVol = (volume * masterVolume).coerceIn(0f, 1f)
        return try {
            playerSearchStreamId = pool.play(soundPlayerSearch, finalVol, finalVol, 1, 0, 1.0f)
            playerSearchStreamId != 0
        } catch (_: Exception) {
            playerSearchStreamId = 0
            false
        }
    }

    private fun fallbackTone(toneType: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            readySoundIds.clear()
        } catch (_: Exception) {}

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }

    companion object {
        private val REWARD_FAMILIES = listOf(
            "cling" to 0.30, "sparkle" to 0.40, "twinkle" to 0.36, "charge" to 0.42,
            "zap" to 0.22, "fanfare" to 0.62, "chime" to 0.50, "thud" to 0.14,
            "burst" to 0.30, "whoosh" to 0.40
        ).toMap()

        private val REWARD_PROFILE_FAMILY = mapOf(
            "coin_cling" to "cling", "lucky_bell" to "cling",
            "crystal_sparkle" to "sparkle", "fairy_sparkle" to "sparkle",
            "star_twinkle" to "twinkle",
            "energy_charge" to "charge", "futuristic_boost" to "charge",
            "electric_zap" to "zap",
            "victory_fanfare" to "fanfare", "medal" to "fanfare", "epic_fantasy" to "fanfare",
            "royal_chime" to "chime", "mystical_chime" to "chime",
            "paper_stamp" to "thud", "key_click" to "thud", "snap" to "thud", "chest_open" to "thud",
            "bright_burst" to "burst", "heartbeat_pop" to "burst",
            "whoosh" to "whoosh"
        )

        private fun tone(freq: Double, t: Double) = sin(2.0 * PI * freq * t)

        private fun rewardWave(family: String, t: Double): Double = when (family) {
            "cling" -> (tone(1760.0, t) + 0.5 * tone(2637.0, t)) * exp(-t * 14.0) * 0.4
            "sparkle" -> {
                val f = listOf(1568.0, 2093.0, 2637.0, 3136.0)[((t / 0.07).toInt()).coerceAtMost(3)]
                tone(f, t) * exp(-(t % 0.07) * 25.0) * exp(-t * 3.0) * 0.4
            }
            "twinkle" -> {
                val f = if (t < 0.12) 1318.5 else 1975.5
                tone(f, t) * exp(-(if (t < 0.12) t else t - 0.12) * 12.0) * 0.4
            }
            "charge" -> tone(220.0 + 900.0 * t * t * 5.0, t) * (t / 0.42).coerceAtMost(1.0) * exp(-t * 2.0) * 0.4
            "zap" -> {
                val saw = ((t * 900.0) % 1.0) * 2.0 - 1.0
                saw * exp(-t * 18.0) * 0.35
            }
            "fanfare" -> {
                val step = (t / 0.12).toInt().coerceAtMost(4)
                val f = listOf(523.25, 659.25, 783.99, 1046.5, 1318.5)[step]
                val local = t - step * 0.12
                (tone(f, t) + 0.3 * tone(f * 2, t)) * exp(-local * (if (step == 4) 4.0 else 9.0)) * 0.4
            }
            "chime" -> (tone(880.0, t) + 0.6 * tone(1320.0, t) + 0.3 * tone(1760.0, t)) * exp(-t * 6.0) * 0.35
            "thud" -> tone(140.0 - t * 400.0, t) * exp(-t * 35.0) * 0.5
            "burst" -> tone(500.0 + t * 3000.0, t) * exp(-t * 14.0) * 0.4
            else -> tone(300.0 + 1200.0 * t, t) * sin(PI * t / 0.40).coerceAtLeast(0.0) * 0.35
        }

        private fun getOrSynthesizeWav(
            targetFile: File,
            durationSec: Double = 0.22,
            sampleRate: Int = 44100,
            generator: (sampleIndex: Int, totalSamples: Int) -> Short
        ): File {
            if (targetFile.exists() && targetFile.length() > 44) {
                return targetFile
            }

            val totalSamples = (sampleRate * durationSec).toInt()
            val dataSize = totalSamples * 2
            val totalFileSize = 36 + dataSize

            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                // RIFF chunk descriptor
                put('R'.code.toByte()); put('I'.code.toByte()); put('F'.code.toByte()); put('F'.code.toByte())
                putInt(totalFileSize)
                put('W'.code.toByte()); put('A'.code.toByte()); put('V'.code.toByte()); put('E'.code.toByte())
                // fmt sub-chunk
                put('f'.code.toByte()); put('m'.code.toByte()); put('t'.code.toByte()); put(' '.code.toByte())
                putInt(16) // Subchunk1Size for PCM
                putShort(1.toShort()) // AudioFormat = 1 (PCM)
                putShort(1.toShort()) // NumChannels = 1 (Mono)
                putInt(sampleRate)
                putInt(sampleRate * 2) // ByteRate = SampleRate * NumChannels * BitsPerSample/8
                putShort(2.toShort()) // BlockAlign
                putShort(16.toShort()) // BitsPerSample
                // data sub-chunk
                put('d'.code.toByte()); put('a'.code.toByte()); put('t'.code.toByte()); put('a'.code.toByte())
                putInt(dataSize)
            }

            val pcmBuffer = ByteBuffer.allocate(dataSize).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until totalSamples) {
                    putShort(generator(i, totalSamples))
                }
            }

            FileOutputStream(targetFile).use { fos ->
                fos.write(header.array())
                fos.write(pcmBuffer.array())
                fos.flush()
            }

            return targetFile
        }
    }
}
