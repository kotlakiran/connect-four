package com.kirankotla.connectfour.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kirankotla.connectfour.data.ConnectFourDatabase
import com.kirankotla.connectfour.data.PlayerPreferences
import com.kirankotla.connectfour.data.SettingsData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PlayerPreferences(application)
    private val dao = ConnectFourDatabase.getDatabase(application).connectFourDao()

    val settings: StateFlow<SettingsData> = preferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsData()
    )

    fun toggleSound(enabled: Boolean) {
        viewModelScope.launch { preferences.updateSound(enabled) }
    }

    fun toggleMusic(enabled: Boolean) {
        viewModelScope.launch { preferences.updateMusic(enabled) }
    }

    fun toggleHaptic(enabled: Boolean) {
        viewModelScope.launch { preferences.updateHaptic(enabled) }
    }

    fun toggleReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferences.updateReduceMotion(enabled) }
    }

    fun toggleHighContrast(enabled: Boolean) {
        viewModelScope.launch { preferences.updateHighContrast(enabled) }
    }

    fun toggleAutoRollDice(enabled: Boolean) {
        viewModelScope.launch { preferences.updateAutoRollDice(enabled) }
    }

    fun setTheme(themeId: String) {
        viewModelScope.launch { preferences.updateSelectedTheme(themeId) }
    }

    fun setMascot(mascotId: String) {
        viewModelScope.launch { preferences.updateSelectedMascot(mascotId) }
    }

    fun resetAllProgress() {
        viewModelScope.launch {
            dao.clearHistory()
            dao.clearSavedGames()
        }
    }
}
