package com.example.engine

import com.example.model.AiDifficulty
import com.example.model.GameMode
import com.example.model.GameRules
import com.example.model.GameState
import com.example.model.GameStatus
import com.example.model.Move
import com.example.model.Player
import com.example.model.PlayerColor
import com.example.model.Token
import com.example.model.TokenState
import com.example.model.TurnPhase
import java.util.UUID
import kotlin.random.Random

object LudoEngine {

    fun initializePlayers(
        playerCount: Int,
        mode: GameMode,
        humanNames: List<String> = emptyList(),
        humanIds: List<String> = emptyList(),
        aiDifficulty: AiDifficulty = AiDifficulty.MEDIUM
    ): List<Player> {
        val clampedCount = playerCount.coerceIn(2, 4)
        // Standard Ludo diagonal colors for 2 players (RED vs YELLOW), or clockwise for 3-4 players
        val colors = if (clampedCount == 2) {
            listOf(PlayerColor.RED, PlayerColor.YELLOW)
        } else {
            PlayerColor.entries.take(clampedCount)
        }

        return (0 until clampedCount).map { idx ->
            val isAiPlayer = mode == GameMode.VS_AI && idx > 0
            val defaultName = when {
                isAiPlayer -> "Royal AI ${idx} (${aiDifficulty.labelEn})"
                idx < humanNames.size && humanNames[idx].isNotBlank() -> humanNames[idx]
                else -> "Player ${idx + 1}"
            }
            val playerId = when {
                idx < humanIds.size && humanIds[idx].isNotBlank() -> humanIds[idx]
                isAiPlayer -> "ai_$idx"
                else -> "local_p$idx"
            }
            Player(
                index = idx,
                id = playerId,
                name = defaultName,
                color = colors[idx],
                isAi = isAiPlayer,
                aiDifficulty = aiDifficulty,
                avatarIndex = idx
            )
        }
    }

    fun createGame(
        mode: GameMode,
        players: List<Player>,
        rules: GameRules = GameRules(),
        gameId: String = "game_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    ): GameState {
        val tokens = players.flatMap { player ->
            (0..3).map { tokenId ->
                Token(
                    id = tokenId,
                    playerIndex = player.index,
                    color = player.color,
                    state = TokenState.BASE,
                    stepsFromStart = -1
                )
            }
        }
        val firstPlayer = players.firstOrNull()
        return GameState(
            gameId = gameId,
            version = 1,
            mode = mode,
            status = GameStatus.PLAYING,
            rules = rules,
            players = players,
            tokens = tokens,
            currentTurnIndex = 0,
            diceValue = 0,
            isRolling = false,
            phase = TurnPhase.WAITING_FOR_ROLL,
            consecutiveSixes = 0,
            lastMove = null,
            winnerIndex = null,
            capturesByPlayer = players.associate { it.index to 0 },
            startedAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
            lastActionId = "create_${UUID.randomUUID().toString().take(8)}",
            statusMessageEn = "${firstPlayer?.name ?: "Player 1"}'s turn — Tap dice to roll!",
            statusMessageHi = "${firstPlayer?.name ?: "खिलाड़ी 1"} की बारी — पासा फेंकें!"
        )
    }

    fun rollDice(
        state: GameState,
        forcedValue: Int? = null,
        random: Random = Random.Default
    ): GameState {
        if (state.status != GameStatus.PLAYING || state.phase != TurnPhase.WAITING_FOR_ROLL) {
            return state
        }
        val rolled = (forcedValue ?: random.nextInt(1, 7)).coerceIn(1, 6)
        val newConsecutiveSixes = if (rolled == 6) state.consecutiveSixes + 1 else 0
        val currentPlayer = state.currentPlayer

        // Rule: 3 consecutive 6s forfeits turn immediately
        if (rolled == 6 && newConsecutiveSixes >= state.rules.maxConsecutiveSixes) {
            val nextIdx = (state.currentTurnIndex + 1) % state.players.size
            val nextPlayer = state.players.getOrNull(nextIdx)
            return state.copy(
                version = state.version + 1,
                diceValue = rolled,
                isRolling = false,
                phase = TurnPhase.WAITING_FOR_ROLL,
                currentTurnIndex = nextIdx,
                consecutiveSixes = 0,
                updatedAtMillis = System.currentTimeMillis(),
                lastActionId = "roll_triple6_${UUID.randomUUID().toString().take(8)}",
                statusMessageEn = "Three 6s in a row! Turn passes to ${nextPlayer?.name ?: "Next"}",
                statusMessageHi = "लगातार तीन 6! अब ${nextPlayer?.name ?: "अगले खिलाड़ी"} की बारी"
            )
        }

        val validMoves = getValidMoves(state, state.currentTurnIndex, rolled)
        return if (validMoves.isEmpty()) {
            // No valid moves -> pass turn to next player automatically
            val nextIdx = (state.currentTurnIndex + 1) % state.players.size
            val nextPlayer = state.players.getOrNull(nextIdx)
            state.copy(
                version = state.version + 1,
                diceValue = rolled,
                isRolling = false,
                phase = TurnPhase.WAITING_FOR_ROLL,
                currentTurnIndex = nextIdx,
                consecutiveSixes = 0,
                updatedAtMillis = System.currentTimeMillis(),
                lastActionId = "roll_nomove_${UUID.randomUUID().toString().take(8)}",
                statusMessageEn = "${currentPlayer?.name} rolled $rolled (No valid moves). ${nextPlayer?.name}'s turn!",
                statusMessageHi = "${currentPlayer?.name} ने $rolled फेंका (कोई चाल नहीं)। ${nextPlayer?.name} की बारी!"
            )
        } else {
            state.copy(
                version = state.version + 1,
                diceValue = rolled,
                isRolling = false,
                phase = TurnPhase.WAITING_FOR_MOVE,
                consecutiveSixes = newConsecutiveSixes,
                updatedAtMillis = System.currentTimeMillis(),
                lastActionId = "roll_${UUID.randomUUID().toString().take(8)}",
                statusMessageEn = "${currentPlayer?.name} rolled $rolled — Select a highlighted token!",
                statusMessageHi = "${currentPlayer?.name} ने $rolled फेंका — अपनी गोटी चुनें!"
            )
        }
    }

    fun getValidMoves(
        state: GameState,
        playerIndex: Int = state.currentTurnIndex,
        diceValue: Int = state.diceValue
    ): List<Move> {
        if (diceValue !in 1..6 || state.status != GameStatus.PLAYING) return emptyList()
        val playerTokens = state.tokens.filter { it.playerIndex == playerIndex }
        val moves = mutableListOf<Move>()

        for (token in playerTokens) {
            when (token.state) {
                TokenState.BASE -> {
                    if (diceValue in state.rules.entryDiceValues) {
                        moves.add(
                            Move(
                                tokenId = token.id,
                                playerIndex = playerIndex,
                                fromSteps = -1,
                                toSteps = 0,
                                fromState = TokenState.BASE,
                                toState = TokenState.ACTIVE,
                                capturedTokens = emptyList(),
                                isHomeFinish = false
                            )
                        )
                    }
                }
                TokenState.ACTIVE, TokenState.HOME -> {
                    val targetSteps = token.stepsFromStart + diceValue
                    if (targetSteps <= 56 || (!state.rules.exactRollToFinish && token.stepsFromStart < 56)) {
                        val clampedSteps = if (!state.rules.exactRollToFinish) {
                            targetSteps.coerceAtMost(56)
                        } else {
                            targetSteps
                        }
                        if (clampedSteps <= 56) {
                            val newState = when {
                                clampedSteps == 56 -> TokenState.FINISHED
                                clampedSteps in 51..55 -> TokenState.HOME
                                else -> TokenState.ACTIVE
                            }
                            val captured = if (newState == TokenState.ACTIVE) {
                                findCapturableOpponentTokens(
                                    state = state,
                                    movingPlayerIndex = playerIndex,
                                    movingColor = token.color,
                                    targetStepsFromStart = clampedSteps
                                )
                            } else {
                                emptyList()
                            }
                            moves.add(
                                Move(
                                    tokenId = token.id,
                                    playerIndex = playerIndex,
                                    fromSteps = token.stepsFromStart,
                                    toSteps = clampedSteps,
                                    fromState = token.state,
                                    toState = newState,
                                    capturedTokens = captured,
                                    isHomeFinish = (newState == TokenState.FINISHED)
                                )
                            )
                        }
                    }
                }
                TokenState.FINISHED -> Unit
            }
        }
        return moves
    }

    fun validateMove(state: GameState, tokenId: Int): Move? {
        if (state.status != GameStatus.PLAYING || state.phase != TurnPhase.WAITING_FOR_MOVE) {
            return null
        }
        return getValidMoves(state, state.currentTurnIndex, state.diceValue)
            .firstOrNull { it.tokenId == tokenId }
    }

    fun moveToken(state: GameState, tokenId: Int): GameState {
        val validMove = validateMove(state, tokenId) ?: return state
        val capturedIds = validMove.capturedTokens.map { it.playerIndex to it.id }.toSet()

        // Update moving token and send captured opponent tokens back to BASE
        val updatedTokens = state.tokens.map { token ->
            when {
                token.playerIndex == validMove.playerIndex && token.id == validMove.tokenId -> {
                    token.copy(
                        state = validMove.toState,
                        stepsFromStart = validMove.toSteps
                    )
                }
                (token.playerIndex to token.id) in capturedIds -> {
                    token.copy(
                        state = TokenState.BASE,
                        stepsFromStart = -1
                    )
                }
                else -> token
            }
        }

        val updatedCaptures = state.capturesByPlayer.toMutableMap()
        if (validMove.capturedTokens.isNotEmpty()) {
            val prev = updatedCaptures[validMove.playerIndex] ?: 0
            updatedCaptures[validMove.playerIndex] = prev + validMove.capturedTokens.size
        }

        val intermediateState = state.copy(
            version = state.version + 1,
            tokens = updatedTokens,
            lastMove = validMove,
            capturesByPlayer = updatedCaptures,
            updatedAtMillis = System.currentTimeMillis(),
            lastActionId = "move_${UUID.randomUUID().toString().take(8)}"
        )

        val winnerIdx = checkWinner(intermediateState)
        if (winnerIdx != null) {
            return finishGame(intermediateState, winnerIdx)
        }

        val grantsExtraTurn = handleExtraTurn(intermediateState, validMove)
        return if (grantsExtraTurn) {
            val currentName = intermediateState.currentPlayer?.name ?: "Player"
            val reasonEn = when {
                validMove.capturedTokens.isNotEmpty() -> "Captured an opponent token! Bonus roll for $currentName!"
                validMove.isHomeFinish -> "Token reached the Royal Crown! Bonus roll for $currentName!"
                else -> "Rolled a 6! Bonus roll for $currentName!"
            }
            val reasonHi = when {
                validMove.capturedTokens.isNotEmpty() -> "विरोधी गोटी काटी! $currentName को बोनस बारी!"
                validMove.isHomeFinish -> "गोटी होम पहुँची! $currentName को बोनस बारी!"
                else -> "6 आया! $currentName को बोनस बारी!"
            }
            intermediateState.copy(
                phase = TurnPhase.WAITING_FOR_ROLL,
                diceValue = 0,
                statusMessageEn = reasonEn,
                statusMessageHi = reasonHi
            )
        } else {
            nextTurn(intermediateState)
        }
    }

    fun findCapturableOpponentTokens(
        state: GameState,
        movingPlayerIndex: Int,
        movingColor: PlayerColor,
        targetStepsFromStart: Int
    ): List<Token> {
        if (targetStepsFromStart !in 0..50) return emptyList()
        val targetGlobalTrack = (movingColor.startOffset + targetStepsFromStart) % 52
        if (BoardGeometry.isSafeTrackIndex(targetGlobalTrack, state.rules.safeCellsEnabled)) {
            return emptyList()
        }
        return state.tokens.filter { other ->
            other.playerIndex != movingPlayerIndex &&
                other.state == TokenState.ACTIVE &&
                other.globalTrackIndex == targetGlobalTrack
        }
    }

    fun captureToken(token: Token): Token {
        return token.copy(state = TokenState.BASE, stepsFromStart = -1)
    }

    fun checkWinner(state: GameState): Int? {
        for (player in state.players) {
            val playerTokens = state.tokens.filter { it.playerIndex == player.index }
            if (playerTokens.size == 4 && playerTokens.all { it.state == TokenState.FINISHED }) {
                return player.index
            }
        }
        return null
    }

    fun handleExtraTurn(state: GameState, move: Move): Boolean {
        val rules = state.rules
        if (rules.extraTurnOnCapture && move.capturedTokens.isNotEmpty()) return true
        if (rules.extraTurnOnHomeFinish && move.isHomeFinish) return true
        if (rules.extraTurnOnSix && state.diceValue == 6) return true
        return false
    }

    fun nextTurn(state: GameState): GameState {
        val nextIdx = (state.currentTurnIndex + 1) % state.players.size
        val nextPlayer = state.players.getOrNull(nextIdx)
        return state.copy(
            currentTurnIndex = nextIdx,
            diceValue = 0,
            isRolling = false,
            phase = TurnPhase.WAITING_FOR_ROLL,
            consecutiveSixes = 0,
            statusMessageEn = "${nextPlayer?.name ?: "Next Player"}'s turn — Tap dice to roll!",
            statusMessageHi = "${nextPlayer?.name ?: "अगला खिलाड़ी"} की बारी — पासा फेंकें!"
        )
    }

    fun finishGame(state: GameState, winnerIndex: Int): GameState {
        val winner = state.players.getOrNull(winnerIndex)
        return state.copy(
            status = GameStatus.FINISHED,
            phase = TurnPhase.GAME_OVER,
            winnerIndex = winnerIndex,
            isRolling = false,
            statusMessageEn = "👑 ${winner?.name ?: "Winner"} conquers the Royal Ludo board!",
            statusMessageHi = "👑 ${winner?.name ?: "विजेता"} ने शाही लूडो मुकाबला जीता!"
        )
    }
}
