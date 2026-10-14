package com.kirankotla.connectfour.audio

import com.kirankotla.connectfour.data.SettingsData
import org.junit.Assert.*
import org.junit.Test

class AudioSettingsTest {

    @Test
    fun testAudioSettings_defaults() {
        val settings = SettingsData()
        assertTrue("Sound should be enabled by default", settings.soundEnabled)
        assertTrue("Music should be enabled by default", settings.musicEnabled)
        assertTrue("Haptics should be enabled by default", settings.hapticEnabled)
    }

    @Test
    fun testAudioSettings_musicToggle() {
        var settings = SettingsData()
        assertTrue(settings.musicEnabled)

        settings = settings.copy(musicEnabled = false)
        assertFalse(settings.musicEnabled)

        settings = settings.copy(musicEnabled = true)
        assertTrue(settings.musicEnabled)
    }

    @Test
    fun testAudioSettings_soundAndHapticsIndependent() {
        var settings = SettingsData()
        settings = settings.copy(soundEnabled = false, musicEnabled = true, hapticEnabled = false)

        assertFalse(settings.soundEnabled)
        assertTrue(settings.musicEnabled)
        assertFalse(settings.hapticEnabled)
    }
}
