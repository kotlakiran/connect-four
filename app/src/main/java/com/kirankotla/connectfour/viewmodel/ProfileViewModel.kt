package com.kirankotla.connectfour.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kirankotla.connectfour.data.*
import kotlinx.coroutines.flow.Flow

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ConnectFourDatabase.getDatabase(application).connectFourDao()

    val profileFlow: Flow<PlayerProfileEntity?> = dao.getPlayerProfileFlow()
    val statsFlow: Flow<PlayerStatsEntity?> = dao.getPlayerStatsFlow()
    val achievementsFlow: Flow<List<AchievementEntity>> = dao.getAllAchievementsFlow()
    val matchHistoryFlow: Flow<List<MatchSummaryEntity>> = dao.getRecentMatchSummariesFlow()
}
