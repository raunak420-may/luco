package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundAndHapticsManager
import com.example.audio.SoundEffect
import com.example.data.local.AppSettings
import com.example.data.local.LocalLudoRepository
import com.example.data.local.LocalMatchHistoryEntity
import com.example.data.remote.FirestoreRepository
import com.example.engine.AiController
import com.example.engine.LudoEngine
import com.example.model.AiDifficulty
import com.example.model.GameMode
import com.example.model.GameResult
import com.example.model.GameRules
import com.example.model.GameState
import com.example.model.GameStatus
import com.example.model.LeaderboardEntry
import com.example.model.MatchRecord
import com.example.model.Move
import com.example.model.OnlineRoom
import com.example.model.TurnPhase
import com.example.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen {
    HOME,
    GAME,
    LOBBY,
    LEADERBOARD,
    ACHIEVEMENTS,
    HISTORY,
    PROFILE,
    SETTINGS
}

enum class ConnectionStatus {
    ONLINE,
    OFFLINE,
    RECONNECTING
}

class RoyalLudoViewModel(
    private val firestoreRepository: FirestoreRepository,
    private val localRepository: LocalLudoRepository,
    private val soundManager: SoundAndHapticsManager,
    val currentUserId: String,
    defaultDisplayName: String
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    val settings: StateFlow<AppSettings> = localRepository.settingsFlow

    val savedOfflineGame: StateFlow<GameState?> = localRepository.savedOfflineGameFlow
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val localHistory: StateFlow<List<LocalMatchHistoryEntity>> = localRepository.localMatchHistoryFlow
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userProfile: StateFlow<UserProfile?> = firestoreRepository.observeUserProfile(currentUserId)
        .catch { e ->
            Log.w("RoyalLudoVM", "Profile stream error", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val leaderboard: StateFlow<List<LeaderboardEntry>> = firestoreRepository.observeLeaderboard()
        .catch { e ->
            Log.w("RoyalLudoVM", "Leaderboard stream error", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cloudMatchHistory: StateFlow<List<MatchRecord>> = firestoreRepository.observeMatchHistory(currentUserId)
        .catch { e ->
            Log.w("RoyalLudoVM", "Match history stream error", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _gameResult = MutableStateFlow<GameResult?>(null)
    val gameResult: StateFlow<GameResult?> = _gameResult.asStateFlow()

    private val _activeOnlineRoom = MutableStateFlow<OnlineRoom?>(null)
    val activeOnlineRoom: StateFlow<OnlineRoom?> = _activeOnlineRoom.asStateFlow()

    private val _reconnectableRoom = MutableStateFlow<OnlineRoom?>(null)
    val reconnectableRoom: StateFlow<OnlineRoom?> = _reconnectableRoom.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.ONLINE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var onlineRoomJob: Job? = null
    private var aiTurnJob: Job? = null
    private var recordedGameIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            firestoreRepository.getOrCreateProfile(defaultDisplayName.ifBlank { "RoyalMonarch" })
            checkForReconnectableOnlineMatch()
        }
    }

    fun navigateTo(screen: AppScreen) {
        playSound(SoundEffect.BUTTON)
        _currentScreen.value = screen
    }

    fun navigateBackToHome() {
        playSound(SoundEffect.BUTTON)
        _gameResult.value = null
        _currentScreen.value = AppScreen.HOME
    }

    fun clearBannerMessage() {
        _bannerMessage.value = null
    }

    // --- Offline & AI Game Setup ---

    fun startOfflinePassAndPlay(playerCount: Int, customNames: List<String> = emptyList()) {
        playSound(SoundEffect.BUTTON)
        onlineRoomJob?.cancel()
        _activeOnlineRoom.value = null
        _gameResult.value = null

        val currentSettings = settings.value
        val rules = GameRules(
            exactRollToFinish = currentSettings.exactRollToFinish,
            safeCellsEnabled = currentSettings.safeCellsEnabled
        )
        val hostName = userProfile.value?.username ?: "Player 1"
        val names = if (customNames.isNotEmpty()) {
            customNames
        } else {
            listOf(hostName, "Player 2", "Player 3", "Player 4").take(playerCount)
        }
        val players = LudoEngine.initializePlayers(
            playerCount = playerCount,
            mode = GameMode.OFFLINE_LOCAL,
            humanNames = names,
            humanIds = listOf(currentUserId, "local_2", "local_3", "local_4").take(playerCount)
        )
        val initial = LudoEngine.createGame(
            mode = GameMode.OFFLINE_LOCAL,
            players = players,
            rules = rules
        )
        _gameState.value = initial
        _currentScreen.value = AppScreen.GAME
        viewModelScope.launch {
            localRepository.saveOfflineGameState(initial)
        }
    }

    fun startAiMatch(playerCount: Int, difficulty: AiDifficulty) {
        playSound(SoundEffect.BUTTON)
        onlineRoomJob?.cancel()
        _activeOnlineRoom.value = null
        _gameResult.value = null

        val currentSettings = settings.value
        val rules = GameRules(
            exactRollToFinish = currentSettings.exactRollToFinish,
            safeCellsEnabled = currentSettings.safeCellsEnabled
        )
        val humanName = userProfile.value?.username ?: "You"
        val players = LudoEngine.initializePlayers(
            playerCount = playerCount,
            mode = GameMode.VS_AI,
            humanNames = listOf(humanName),
            humanIds = listOf(currentUserId),
            aiDifficulty = difficulty
        )
        val initial = LudoEngine.createGame(
            mode = GameMode.VS_AI,
            players = players,
            rules = rules
        )
        _gameState.value = initial
        _currentScreen.value = AppScreen.GAME
        viewModelScope.launch {
            localRepository.saveOfflineGameState(initial)
        }
    }

    fun resumeSavedOfflineMatch() {
        val saved = savedOfflineGame.value ?: return
        playSound(SoundEffect.BUTTON)
        onlineRoomJob?.cancel()
        _activeOnlineRoom.value = null
        _gameResult.value = null
        _gameState.value = saved
        _currentScreen.value = AppScreen.GAME
        scheduleAiTurnIfNeeded(saved)
    }

    // --- Core Gameplay Actions (Dice Roll & Token Move) ---

    fun onRollDiceClicked() {
        val state = _gameState.value ?: return
        if (state.status != GameStatus.PLAYING || state.phase != TurnPhase.WAITING_FOR_ROLL || state.isRolling) {
            return
        }
        val activePlayer = state.currentPlayer ?: return

        // In VS_AI mode, human only rolls on their own turn
        if (state.mode == GameMode.VS_AI && activePlayer.isAi) return

        // In Online mode, validate turn ownership
        if (state.mode == GameMode.QUICK_MATCH || state.mode == GameMode.PRIVATE_ROOM) {
            if (activePlayer.id != currentUserId) {
                _bannerMessage.value = "Wait for ${activePlayer.name}'s turn!"
                return
            }
        }

        viewModelScope.launch {
            playSound(SoundEffect.DICE_ROLL)
            _gameState.value = state.copy(isRolling = true)
            delay(if (settings.value.isReducedMotion) 90L else 360L)

            val rolledState = LudoEngine.rollDice(state)
            _gameState.value = rolledState

            if (rolledState.mode == GameMode.OFFLINE_LOCAL || rolledState.mode == GameMode.VS_AI) {
                localRepository.saveOfflineGameState(rolledState)
                // Auto-move if only 1 valid move and it's a BASE exit or single active token
                val validMoves = LudoEngine.getValidMoves(rolledState)
                if (rolledState.phase == TurnPhase.WAITING_FOR_MOVE && validMoves.size == 1) {
                    delay(if (settings.value.isReducedMotion) 80L else 280L)
                    executeTokenMove(validMoves.first().tokenId)
                } else {
                    scheduleAiTurnIfNeeded(rolledState)
                }
            } else {
                syncOnlineTurnState(rolledState, expectedVersion = state.version)
            }
        }
    }

    fun onTokenSelected(tokenId: Int) {
        val state = _gameState.value ?: return
        if (state.status != GameStatus.PLAYING || state.phase != TurnPhase.WAITING_FOR_MOVE) return
        val activePlayer = state.currentPlayer ?: return

        if (state.mode == GameMode.VS_AI && activePlayer.isAi) return
        if ((state.mode == GameMode.QUICK_MATCH || state.mode == GameMode.PRIVATE_ROOM) && activePlayer.id != currentUserId) {
            return
        }

        val validMove = LudoEngine.validateMove(state, tokenId)
        if (validMove == null) {
            _bannerMessage.value = "Select a highlighted token that can legally move ${state.diceValue} steps."
            return
        }
        viewModelScope.launch {
            executeTokenMove(tokenId)
        }
    }

    private suspend fun executeTokenMove(tokenId: Int) {
        val beforeState = _gameState.value ?: return
        val validMove: Move = LudoEngine.validateMove(beforeState, tokenId) ?: return
        val afterState = LudoEngine.moveToken(beforeState, tokenId)

        when {
            afterState.status == GameStatus.FINISHED -> playSound(SoundEffect.VICTORY)
            validMove.capturedTokens.isNotEmpty() -> playSound(SoundEffect.CAPTURE)
            validMove.isHomeFinish -> playSound(SoundEffect.HOME_REACHED)
            else -> playSound(SoundEffect.TOKEN_MOVE)
        }

        _gameState.value = afterState

        if (afterState.mode == GameMode.OFFLINE_LOCAL || afterState.mode == GameMode.VS_AI) {
            localRepository.saveOfflineGameState(afterState)
            if (afterState.status == GameStatus.FINISHED) {
                finalizeCompletedMatch(afterState)
            } else {
                scheduleAiTurnIfNeeded(afterState)
            }
        } else {
            syncOnlineTurnState(afterState, expectedVersion = beforeState.version)
            if (afterState.status == GameStatus.FINISHED) {
                finalizeCompletedMatch(afterState)
            }
        }
    }

    private fun scheduleAiTurnIfNeeded(state: GameState) {
        aiTurnJob?.cancel()
        if (state.status != GameStatus.PLAYING) return
        val activePlayer = state.currentPlayer ?: return
        if (!activePlayer.isAi) return

        aiTurnJob = viewModelScope.launch {
            delay(if (settings.value.isReducedMotion) 220L else 620L)
            val current = _gameState.value ?: return@launch
            if (current.currentPlayer?.isAi != true || current.status != GameStatus.PLAYING) return@launch

            if (current.phase == TurnPhase.WAITING_FOR_ROLL) {
                playSound(SoundEffect.DICE_ROLL)
                _gameState.value = current.copy(isRolling = true)
                delay(if (settings.value.isReducedMotion) 90L else 360L)
                val afterRoll = LudoEngine.rollDice(current)
                _gameState.value = afterRoll
                localRepository.saveOfflineGameState(afterRoll)

                if (afterRoll.phase == TurnPhase.WAITING_FOR_MOVE && afterRoll.currentPlayer?.isAi == true) {
                    delay(if (settings.value.isReducedMotion) 150L else 480L)
                    val chosenMove = AiController.selectBestMove(afterRoll, activePlayer.aiDifficulty)
                    if (chosenMove != null) {
                        executeTokenMove(chosenMove.tokenId)
                    }
                } else if (afterRoll.currentPlayer?.isAi == true) {
                    scheduleAiTurnIfNeeded(afterRoll)
                }
            } else if (current.phase == TurnPhase.WAITING_FOR_MOVE) {
                val chosenMove = AiController.selectBestMove(current, activePlayer.aiDifficulty)
                if (chosenMove != null) {
                    executeTokenMove(chosenMove.tokenId)
                }
            }
        }
    }

    private fun finalizeCompletedMatch(completedState: GameState) {
        if (completedState.gameId in recordedGameIds) return
        recordedGameIds.add(completedState.gameId)

        val winnerIndex = completedState.winnerIndex ?: 0
        val winner = completedState.players.getOrNull(winnerIndex) ?: completedState.players.first()
        val isHumanWin = winner.id == currentUserId || (completedState.mode == GameMode.OFFLINE_LOCAL && winnerIndex == 0)
        val durationSec = ((System.currentTimeMillis() - completedState.startedAtMillis) / 1000L)
            .toInt()
            .coerceAtLeast(15)
        val humanCaptures = completedState.capturesByPlayer[0] ?: 0
        val opponentsText = completedState.players
            .filter { it.id != currentUserId }
            .joinToString(", ") { it.name }
            .ifBlank { "Local Players" }

        val coinsEarned = if (isHumanWin) 200 else 40
        val xpEarned = if (isHumanWin) 120 else 35

        _gameResult.value = GameResult(
            gameId = completedState.gameId,
            mode = completedState.mode,
            winnerPlayer = winner,
            players = completedState.players,
            durationSeconds = durationSec,
            coinsEarned = coinsEarned,
            xpEarned = xpEarned
        )

        viewModelScope.launch {
            localRepository.clearSavedOfflineGame()
            localRepository.insertLocalMatchHistory(
                LocalMatchHistoryEntity(
                    matchId = completedState.gameId,
                    gameMode = completedState.mode.name,
                    opponentNames = opponentsText,
                    winnerName = winner.name,
                    isWin = isHumanWin,
                    durationSeconds = durationSec,
                    coinsDelta = coinsEarned,
                    xpEarned = xpEarned
                )
            )
            firestoreRepository.recordMatchAndRewards(
                gameMode = completedState.mode.name,
                opponentNames = opponentsText,
                winnerName = winner.name,
                isWin = isHumanWin,
                durationSeconds = durationSec,
                capturesInMatch = humanCaptures
            )
        }
    }

    // --- Online Quick Match, Private Rooms & Reconnection ---

    fun openLobbyScreen() {
        playSound(SoundEffect.BUTTON)
        _currentScreen.value = AppScreen.LOBBY
    }

    fun createPrivateRoom(maxPlayers: Int = 2) {
        viewModelScope.launch {
            _isBusy.value = true
            val hostName = userProfile.value?.username ?: "RoyalHost"
            val initialPlayers = LudoEngine.initializePlayers(
                playerCount = maxPlayers,
                mode = GameMode.PRIVATE_ROOM,
                humanNames = listOf(hostName),
                humanIds = listOf(currentUserId)
            )
            val templateGame = LudoEngine.createGame(
                mode = GameMode.PRIVATE_ROOM,
                players = initialPlayers
            )
            val res = firestoreRepository.createOnlineRoom(
                hostName = hostName,
                mode = "PRIVATE_ROOM",
                maxPlayers = maxPlayers,
                initialBoardJson = templateGame.toCompactBoardJson()
            )
            _isBusy.value = false
            res.onSuccess { room ->
                subscribeToOnlineRoom(room.roomId)
                _currentScreen.value = AppScreen.LOBBY
                _bannerMessage.value = "Private Room Created! Share Code: ${room.roomCode}"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Could not create room"
            }
        }
    }

    fun joinPrivateRoomByCode(code: String) {
        if (code.trim().length < 4) {
            _bannerMessage.value = "Please enter a valid 6-character room code."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val lookup = firestoreRepository.findWaitingRoomByCode(code)
            val room = lookup.getOrNull()
            if (room == null) {
                _isBusy.value = false
                _bannerMessage.value = "No waiting room found with code ${code.uppercase()}"
                return@launch
            }
            val myName = userProfile.value?.username ?: "RoyalChallenger"
            val joinRes = firestoreRepository.joinOrReadyRoom(
                roomId = room.roomId,
                playerName = myName,
                markReady = true,
                startGameNow = false
            )
            _isBusy.value = false
            joinRes.onSuccess { updatedRoom ->
                subscribeToOnlineRoom(updatedRoom.roomId)
                _currentScreen.value = AppScreen.LOBBY
                _bannerMessage.value = "Joined Room ${updatedRoom.roomCode}!"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Failed to join room"
            }
        }
    }

    fun startOnlineQuickMatch() {
        viewModelScope.launch {
            _isBusy.value = true
            val myName = userProfile.value?.username ?: "RoyalKnight"
            val openRoom = firestoreRepository.findOpenQuickMatchRoom().getOrNull()
            if (openRoom != null && !openRoom.playerUids.contains(currentUserId)) {
                val allUids = openRoom.playerUids + currentUserId
                val allNames = openRoom.playerNames + myName
                val players = LudoEngine.initializePlayers(
                    playerCount = allUids.size.coerceIn(2, 4),
                    mode = GameMode.QUICK_MATCH,
                    humanNames = allNames,
                    humanIds = allUids
                )
                val readyState = LudoEngine.createGame(
                    mode = GameMode.QUICK_MATCH,
                    players = players,
                    gameId = openRoom.roomId
                )
                val joinRes = firestoreRepository.joinOrReadyRoom(
                    roomId = openRoom.roomId,
                    playerName = myName,
                    markReady = true,
                    startGameNow = true,
                    updatedBoardJson = readyState.toCompactBoardJson()
                )
                _isBusy.value = false
                joinRes.onSuccess { room ->
                    subscribeToOnlineRoom(room.roomId)
                    _currentScreen.value = AppScreen.GAME
                }.onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Quick Match join failed"
                }
            } else {
                // Create a new Quick Match room and wait for opponent (or allow instant start with Royal Bot fill)
                val initialPlayers = LudoEngine.initializePlayers(
                    playerCount = 2,
                    mode = GameMode.QUICK_MATCH,
                    humanNames = listOf(myName, "Challenger"),
                    humanIds = listOf(currentUserId, "waiting_slot")
                )
                val initialGame = LudoEngine.createGame(
                    mode = GameMode.QUICK_MATCH,
                    players = initialPlayers
                )
                val createRes = firestoreRepository.createOnlineRoom(
                    hostName = myName,
                    mode = "QUICK_MATCH",
                    maxPlayers = 2,
                    initialBoardJson = initialGame.toCompactBoardJson()
                )
                _isBusy.value = false
                createRes.onSuccess { room ->
                    subscribeToOnlineRoom(room.roomId)
                    _currentScreen.value = AppScreen.LOBBY
                    _bannerMessage.value = "Searching for online players (Room ${room.roomCode})..."
                }.onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Quick Match creation failed"
                }
            }
        }
    }

    fun hostStartOnlineRoomMatch() {
        val room = _activeOnlineRoom.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val uids = if (room.playerUids.size >= 2) {
                room.playerUids
            } else {
                // If only 1 human in lobby and they click Start Now, pair with an online Royal Bot slot so they can test/play immediately
                room.playerUids + listOf(currentUserId)
            }
            val names = if (room.playerNames.size >= 2) {
                room.playerNames
            } else {
                room.playerNames + listOf("Royal Challenger")
            }
            val players = LudoEngine.initializePlayers(
                playerCount = uids.size.coerceIn(2, 4),
                mode = if (room.mode == "QUICK_MATCH") GameMode.QUICK_MATCH else GameMode.PRIVATE_ROOM,
                humanNames = names,
                humanIds = uids
            )
            val startedState = LudoEngine.createGame(
                mode = if (room.mode == "QUICK_MATCH") GameMode.QUICK_MATCH else GameMode.PRIVATE_ROOM,
                players = players,
                gameId = room.roomId
            )
            val res = firestoreRepository.joinOrReadyRoom(
                roomId = room.roomId,
                playerName = userProfile.value?.username ?: "Host",
                markReady = true,
                startGameNow = true,
                updatedBoardJson = startedState.toCompactBoardJson()
            )
            _isBusy.value = false
            res.onSuccess {
                _currentScreen.value = AppScreen.GAME
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Could not start room match"
            }
        }
    }

    fun checkForReconnectableOnlineMatch() {
        viewModelScope.launch {
            val res = firestoreRepository.findReconnectableRoomForUser(currentUserId)
            _reconnectableRoom.value = res.getOrNull()
        }
    }

    fun reconnectToOnlineRoom(room: OnlineRoom) {
        playSound(SoundEffect.BUTTON)
        _connectionStatus.value = ConnectionStatus.RECONNECTING
        subscribeToOnlineRoom(room.roomId)
        _currentScreen.value = if (room.status == "PLAYING") AppScreen.GAME else AppScreen.LOBBY
        _bannerMessage.value = "Reconnected to Room ${room.roomCode} (v${room.version})"
    }

    private fun subscribeToOnlineRoom(roomId: String) {
        onlineRoomJob?.cancel()
        onlineRoomJob = viewModelScope.launch {
            firestoreRepository.observeOnlineRoom(roomId)
                .catch { e ->
                    Log.w("RoyalLudoVM", "Online room connection interrupted", e)
                    _connectionStatus.value = ConnectionStatus.RECONNECTING
                }
                .collect { room ->
                    _connectionStatus.value = ConnectionStatus.ONLINE
                    _activeOnlineRoom.value = room
                    if (room != null && (room.status == "PLAYING" || room.status == "FINISHED")) {
                        val restoredState = GameState.fromCompactBoardJson(
                            gameId = room.roomId,
                            version = room.version,
                            mode = if (room.mode == "QUICK_MATCH") GameMode.QUICK_MATCH else GameMode.PRIVATE_ROOM,
                            status = runCatching { GameStatus.valueOf(room.status) }.getOrDefault(GameStatus.PLAYING),
                            currentTurnIndex = room.currentTurnIndex,
                            diceValue = room.diceValue,
                            phase = runCatching { TurnPhase.valueOf(room.phase) }.getOrDefault(TurnPhase.WAITING_FOR_ROLL),
                            winnerUid = room.winnerUid,
                            lastActionId = room.lastActionId,
                            jsonStr = room.boardStateJson
                        )
                        _gameState.value = restoredState
                        if (_currentScreen.value == AppScreen.LOBBY && room.status == "PLAYING") {
                            _currentScreen.value = AppScreen.GAME
                        }
                        if (restoredState.status == GameStatus.FINISHED) {
                            finalizeCompletedMatch(restoredState)
                        }
                    }
                }
        }
    }

    private suspend fun syncOnlineTurnState(updatedState: GameState, expectedVersion: Int) {
        val room = _activeOnlineRoom.value ?: return
        val nextTurnPlayer = updatedState.players.getOrNull(updatedState.currentTurnIndex)
        val winnerUid = updatedState.winnerIndex?.let { idx ->
            updatedState.players.getOrNull(idx)?.id
        } ?: ""

        val res = firestoreRepository.submitValidatedTurnUpdate(
            roomId = room.roomId,
            expectedVersion = expectedVersion,
            actionId = "act_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            nextTurnIndex = updatedState.currentTurnIndex,
            nextTurnUid = nextTurnPlayer?.id ?: currentUserId,
            diceValue = updatedState.diceValue,
            phase = updatedState.phase.name,
            boardStateJson = updatedState.toCompactBoardJson(),
            status = updatedState.status.name,
            winnerUid = winnerUid
        )
        res.onFailure { err ->
            _bannerMessage.value = "Sync validation: ${err.localizedMessage ?: "Re-syncing board state"}"
        }
    }

    // --- Profile & Settings Customization ---

    fun updateProfile(newUsername: String, newAvatarIndex: Int) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = firestoreRepository.updateProfileCustomization(newUsername, newAvatarIndex)
            _isBusy.value = false
            res.onSuccess {
                _bannerMessage.value = "Royal Profile Updated!"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        localRepository.updateSettings(transform)
    }

    private fun playSound(effect: SoundEffect) {
        val s = settings.value
        soundManager.playEffect(
            effect = effect,
            soundEnabled = s.isSoundEnabled,
            vibrationEnabled = s.isVibrationEnabled
        )
    }
}
