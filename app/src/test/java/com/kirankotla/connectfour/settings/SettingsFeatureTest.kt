package com.kirankotla.connectfour.settings

import com.kirankotla.connectfour.data.SettingsData
import com.kirankotla.connectfour.data.ThemeMode
import org.junit.Assert.*
import org.junit.Test

class SettingsFeatureTest {

    @Test
    fun testSettingsData_defaults() {
        val settings = SettingsData()
        assertEquals("Player 1", settings.playerName)
        assertEquals(18, settings.playerAge)
        assertTrue(settings.soundEnabled)
        assertTrue(settings.musicEnabled)
        assertTrue(settings.hapticEnabled)
        assertFalse(settings.reduceMotion)
        assertFalse(settings.highContrast)
        assertFalse(settings.autoRollDice)
        assertTrue(settings.showEdgePreview)
        assertEquals("paper_workshop", settings.selectedThemeId)
        assertEquals("piko", settings.selectedMascotId)
        assertFalse(settings.tutorialCompleted)
        assertFalse(settings.onboardingCompleted)
        assertTrue(settings.turnTimerEnabled)
        assertEquals(15, settings.turnTimerSeconds)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    }

    @Test
    fun testSettingsData_themeModeSwitching() {
        var settings = SettingsData(themeMode = ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)

        settings = settings.copy(themeMode = ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.themeMode)

        settings = settings.copy(themeMode = ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
    }

    @Test
    fun testSettingsData_accessibilityToggles() {
        var settings = SettingsData()
        assertFalse(settings.reduceMotion)
        assertFalse(settings.highContrast)

        settings = settings.copy(reduceMotion = true, highContrast = true)
        assertTrue(settings.reduceMotion)
        assertTrue(settings.highContrast)
    }

    @Test
    fun testSettingsData_audioToggles() {
        var settings = SettingsData()
        assertTrue(settings.soundEnabled)
        assertTrue(settings.hapticEnabled)

        settings = settings.copy(soundEnabled = false, hapticEnabled = false)
        assertFalse(settings.soundEnabled)
        assertFalse(settings.hapticEnabled)
    }
}
