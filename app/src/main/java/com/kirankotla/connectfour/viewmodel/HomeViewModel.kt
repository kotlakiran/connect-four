package com.kirankotla.connectfour.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kirankotla.connectfour.data.ConnectFourDatabase
import com.kirankotla.connectfour.data.SavedGameEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ConnectFourDatabase.getDatabase(application).connectFourDao()

    private val _resumableGame = MutableStateFlow<SavedGameEntity?>(null)
    val resumableGame: StateFlow<SavedGameEntity?> = _resumableGame.asStateFlow()

    fun checkForResumableGame() {
        viewModelScope.launch {
            _resumableGame.value = dao.getLatestSavedGame()
        }
    }
}
