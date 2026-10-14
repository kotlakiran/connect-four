package com.kirankotla.connectfour.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "connectfour_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class SettingsData(
    val playerName: String = "Player 1",
    val playerAge: Int? = 18,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val autoRollDice: Boolean = false,
    val showEdgePreview: Boolean = true,
    val selectedThemeId: String = "paper_workshop",
    val selectedMascotId: String = "piko",
    val tutorialCompleted: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val turnTimerEnabled: Boolean = true,
    val turnTimerSeconds: Int = 15,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class PlayerPreferences(private val context: Context) {

    private val KEY_NAME = stringPreferencesKey("player_name")
    private val KEY_AGE = intPreferencesKey("player_age")
    private val KEY_SOUND = booleanPreferencesKey("sound_enabled")
    private val KEY_MUSIC = booleanPreferencesKey("music_enabled")
    private val KEY_HAPTIC = booleanPreferencesKey("haptic_enabled")
    private val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    private val KEY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
    private val KEY_AUTO_ROLL_DICE = booleanPreferencesKey("auto_roll_dice")
    private val KEY_SHOW_PREVIEW = booleanPreferencesKey("show_preview")
    private val KEY_THEME = stringPreferencesKey("selected_theme")
    private val KEY_MASCOT = stringPreferencesKey("selected_mascot")
    private val KEY_TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
    private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val KEY_TURN_TIMER_ENABLED = booleanPreferencesKey("turn_timer_enabled")
    private val KEY_TURN_TIMER_SECONDS = intPreferencesKey("turn_timer_seconds")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

    val settingsFlow: Flow<SettingsData> = context.dataStore.data.map { prefs ->
        SettingsData(
            playerName = prefs[KEY_NAME] ?: "Player 1",
            playerAge = prefs[KEY_AGE],
            soundEnabled = prefs[KEY_SOUND] ?: true,
            musicEnabled = prefs[KEY_MUSIC] ?: true,
            hapticEnabled = prefs[KEY_HAPTIC] ?: true,
            reduceMotion = prefs[KEY_REDUCE_MOTION] ?: false,
            highContrast = prefs[KEY_HIGH_CONTRAST] ?: false,
            autoRollDice = prefs[KEY_AUTO_ROLL_DICE] ?: false,
            showEdgePreview = prefs[KEY_SHOW_PREVIEW] ?: true,
            selectedThemeId = prefs[KEY_THEME] ?: "paper_workshop",
            selectedMascotId = prefs[KEY_MASCOT] ?: "piko",
            tutorialCompleted = prefs[KEY_TUTORIAL_COMPLETED] ?: false,
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
            turnTimerEnabled = prefs[KEY_TURN_TIMER_ENABLED] ?: true,
            turnTimerSeconds = prefs[KEY_TURN_TIMER_SECONDS] ?: 15,
            themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        )
    }

    suspend fun updatePlayerProfile(name: String, age: Int?) {
        context.dataStore.edit {
            it[KEY_NAME] = name
            if (age == null) it.remove(KEY_AGE) else it[KEY_AGE] = age
        }
    }

    suspend fun updateSound(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND] = enabled }
    }

    suspend fun updateMusic(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MUSIC] = enabled }
    }

    suspend fun updateHaptic(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC] = enabled }
    }

    suspend fun updateReduceMotion(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REDUCE_MOTION] = enabled }
    }

    suspend fun updateHighContrast(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HIGH_CONTRAST] = enabled }
    }

    suspend fun updateAutoRollDice(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_ROLL_DICE] = enabled }
    }

    suspend fun updateSelectedTheme(themeId: String) {
        context.dataStore.edit { it[KEY_THEME] = themeId }
    }

    suspend fun updateSelectedMascot(mascotId: String) {
        context.dataStore.edit { it[KEY_MASCOT] = mascotId }
    }

    suspend fun setTutorialCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_TUTORIAL_COMPLETED] = completed }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun updateTurnTimerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TURN_TIMER_ENABLED] = enabled }
    }

    suspend fun updateTurnTimerSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_TURN_TIMER_SECONDS] = seconds }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }
}
