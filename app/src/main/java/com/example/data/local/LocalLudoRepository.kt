package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.model.GameMode
import com.example.model.GameState
import com.example.model.GameStatus
import com.example.model.TurnPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val isSoundEnabled: Boolean = true,
    val isMusicEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isNotificationsEnabled: Boolean = true,
    val isReducedMotion: Boolean = false,
    val languageCode: String = "en", // "en" or "hi"
    val exactRollToFinish: Boolean = true,
    val safeCellsEnabled: Boolean = true
)

class LocalLudoRepository(
    context: Context,
    private val savedGameDao: SavedGameDao,
    private val historyDao: LocalMatchHistoryDao
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("royal_ludo_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    val savedOfflineGameFlow: Flow<GameState?> = savedGameDao.observeSavedOfflineGame().map { entity ->
        entity?.toGameState()
    }

    val localMatchHistoryFlow: Flow<List<LocalMatchHistoryEntity>> = historyDao.observeLocalHistory()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            isSoundEnabled = prefs.getBoolean("sound_enabled", true),
            isMusicEnabled = prefs.getBoolean("music_enabled", true),
            isVibrationEnabled = prefs.getBoolean("vibration_enabled", true),
            isNotificationsEnabled = prefs.getBoolean("notifications_enabled", true),
            isReducedMotion = prefs.getBoolean("reduced_motion", false),
            languageCode = prefs.getString("language_code", "en") ?: "en",
            exactRollToFinish = prefs.getBoolean("exact_roll", true),
            safeCellsEnabled = prefs.getBoolean("safe_cells", true)
        )
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settingsFlow.value)
        prefs.edit()
            .putBoolean("sound_enabled", updated.isSoundEnabled)
            .putBoolean("music_enabled", updated.isMusicEnabled)
            .putBoolean("vibration_enabled", updated.isVibrationEnabled)
            .putBoolean("notifications_enabled", updated.isNotificationsEnabled)
            .putBoolean("reduced_motion", updated.isReducedMotion)
            .putString("language_code", updated.languageCode)
            .putBoolean("exact_roll", updated.exactRollToFinish)
            .putBoolean("safe_cells", updated.safeCellsEnabled)
            .apply()
        _settingsFlow.value = updated
    }

    suspend fun saveOfflineGameState(state: GameState) {
        if (state.status == GameStatus.FINISHED) {
            savedGameDao.clearSavedOfflineGame()
            return
        }
        val entity = SavedGameEntity(
            id = "active_offline_game",
            gameId = state.gameId,
            mode = state.mode.name,
            status = state.status.name,
            currentTurnIndex = state.currentTurnIndex,
            diceValue = state.diceValue,
            phase = state.phase.name,
            version = state.version,
            boardStateJson = state.toCompactBoardJson(),
            updatedAtMillis = System.currentTimeMillis()
        )
        savedGameDao.upsertSavedGame(entity)
    }

    suspend fun loadSavedOfflineGame(): GameState? {
        return savedGameDao.getSavedOfflineGame()?.toGameState()
    }

    suspend fun clearSavedOfflineGame() {
        savedGameDao.clearSavedOfflineGame()
    }

    suspend fun insertLocalMatchHistory(entry: LocalMatchHistoryEntity) {
        historyDao.insertMatch(entry)
    }

    private fun SavedGameEntity.toGameState(): GameState {
        return GameState.fromCompactBoardJson(
            gameId = gameId,
            version = version,
            mode = runCatching { GameMode.valueOf(mode) }.getOrDefault(GameMode.OFFLINE_LOCAL),
            status = runCatching { GameStatus.valueOf(status) }.getOrDefault(GameStatus.PLAYING),
            currentTurnIndex = currentTurnIndex,
            diceValue = diceValue,
            phase = runCatching { TurnPhase.valueOf(phase) }.getOrDefault(TurnPhase.WAITING_FOR_ROLL),
            winnerUid = "",
            lastActionId = "restored_$version",
            jsonStr = boardStateJson
        )
    }
}
