package com.example.engine

import com.example.model.AiDifficulty
import com.example.model.GameState
import com.example.model.Move
import com.example.model.PlayerColor
import com.example.model.TokenState
import kotlin.random.Random

object AiController {

    /**
     * Selects a valid move for the current AI player according to its configured difficulty.
     * Never cheats or peeks at future dice rolls.
     */
    fun selectBestMove(
        state: GameState,
        difficulty: AiDifficulty = state.currentPlayer?.aiDifficulty ?: AiDifficulty.MEDIUM,
        random: Random = Random.Default
    ): Move? {
        val validMoves = LudoEngine.getValidMoves(state, state.currentTurnIndex, state.diceValue)
        if (validMoves.isEmpty()) return null
        if (validMoves.size == 1) return validMoves.first()

        return when (difficulty) {
            AiDifficulty.EASY -> validMoves.random(random)
            AiDifficulty.MEDIUM -> selectMediumMove(state, validMoves)
            AiDifficulty.HARD -> selectHardMove(state, validMoves)
        }
    }

    private fun selectMediumMove(state: GameState, validMoves: List<Move>): Move {
        return validMoves.maxByOrNull { move ->
            var score = 0
            // 1. Prefer capturing opponent tokens
            if (move.capturedTokens.isNotEmpty()) score += 400
            // 2. Prefer bringing a token out of BASE
            if (move.fromState == TokenState.BASE) score += 250
            // 3. Prefer finishing a token
            if (move.isHomeFinish) score += 300
            // 4. Prefer landing on a safe square
            val color = state.players.getOrNull(move.playerIndex)?.color ?: PlayerColor.RED
            if (move.toState == TokenState.ACTIVE) {
                val destTrack = (color.startOffset + move.toSteps) % 52
                if (BoardGeometry.isSafeTrackIndex(destTrack, state.rules.safeCellsEnabled)) {
                    score += 120
                }
            }
            // 5. Advance furthest token slightly
            score += move.toSteps
            score
        } ?: validMoves.first()
    }

    private fun selectHardMove(state: GameState, validMoves: List<Move>): Move {
        val aiPlayer = state.players.getOrNull(state.currentTurnIndex)
        val aiColor = aiPlayer?.color ?: PlayerColor.RED
        val opponentActiveTracks = state.tokens
            .filter { it.playerIndex != state.currentTurnIndex && it.state == TokenState.ACTIVE }
            .mapNotNull { it.globalTrackIndex }

        return validMoves.maxByOrNull { move ->
            var score = 0

            // 1. Capture opportunity weighted by how close the opponent was to finishing
            if (move.capturedTokens.isNotEmpty()) {
                val maxOpponentProgress = move.capturedTokens.maxOf { it.stepsFromStart.coerceAtLeast(1) }
                score += 600 + (maxOpponentProgress * 8)
            }

            // 2. Finishing a token into the central crown (grants extra turn + permanent safety)
            if (move.isHomeFinish) {
                score += 520
            }

            // 3. Entering the private colored HOME lane (100% immune to capture)
            if (move.fromState == TokenState.ACTIVE && move.toState == TokenState.HOME) {
                score += 380
            }

            // 4. Unlocking a token from BASE on a 6
            if (move.fromState == TokenState.BASE && move.toState == TokenState.ACTIVE) {
                score += 340
            }

            // 5. Tactical threat & safety evaluation on the 52-cell main track
            val currentTrack = if (move.fromState == TokenState.ACTIVE && move.fromSteps in 0..50) {
                (aiColor.startOffset + move.fromSteps) % 52
            } else null

            val destTrack = if (move.toState == TokenState.ACTIVE && move.toSteps in 0..50) {
                (aiColor.startOffset + move.toSteps) % 52
            } else null

            // Check if currently in danger (an opponent is 1..6 steps behind on a non-safe cell)
            if (currentTrack != null && !BoardGeometry.isSafeTrackIndex(currentTrack, state.rules.safeCellsEnabled)) {
                val threatsBehindNow = countThreatsBehind(currentTrack, opponentActiveTracks)
                if (threatsBehindNow > 0) {
                    // Escaping danger is high priority!
                    score += 240 * threatsBehindNow
                }
            }

            // Check destination safety
            if (destTrack != null) {
                if (BoardGeometry.isSafeTrackIndex(destTrack, state.rules.safeCellsEnabled)) {
                    score += 180
                } else {
                    val threatsBehindDest = countThreatsBehind(destTrack, opponentActiveTracks)
                    if (threatsBehindDest > 0) {
                        // Moving into immediate capture range is penalized
                        score -= 210 * threatsBehindDest
                    }
                    // Stalking bonus: landing 1..6 steps behind an opponent on an unsafe square
                    val targetsAhead = countTargetsAhead(destTrack, opponentActiveTracks, state.rules.safeCellsEnabled)
                    score += 65 * targetsAhead
                }
            }

            // 6. Progress tie-breaker
            score += move.toSteps * 3
            score
        } ?: validMoves.first()
    }

    private fun countThreatsBehind(trackIndex: Int, opponentTracks: List<Int>): Int {
        return opponentTracks.count { oppTrack ->
            val distance = (trackIndex - oppTrack + 52) % 52
            distance in 1..6
        }
    }

    private fun countTargetsAhead(trackIndex: Int, opponentTracks: List<Int>, safeCellsEnabled: Boolean): Int {
        return opponentTracks.count { oppTrack ->
            val distance = (oppTrack - trackIndex + 52) % 52
            distance in 1..6 && !BoardGeometry.isSafeTrackIndex(oppTrack, safeCellsEnabled)
        }
    }
}
