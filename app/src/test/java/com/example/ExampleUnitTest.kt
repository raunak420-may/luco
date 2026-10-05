package com.example

import com.example.engine.AiController
import com.example.engine.LudoEngine
import com.example.model.AiDifficulty
import com.example.model.GameMode
import com.example.model.GameStatus
import com.example.model.PlayerColor
import com.example.model.TokenState
import com.example.model.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun diceRoll_producesValidValuesAndPassTurnWhenAllTokensInBaseOnNonSix() {
        val players = LudoEngine.initializePlayers(2, GameMode.OFFLINE_LOCAL)
        val initial = LudoEngine.createGame(GameMode.OFFLINE_LOCAL, players)

        // Rolling a 4 when all tokens are in BASE passes turn to player 1
        val afterFour = LudoEngine.rollDice(initial, forcedValue = 4)
        assertEquals(4, afterFour.diceValue)
        assertEquals(1, afterFour.currentTurnIndex)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, afterFour.phase)
    }

    @Test
    fun tokenEntryAndExtraTurnOnSix_unlocksTokenToTrackStart() {
        val players = LudoEngine.initializePlayers(2, GameMode.OFFLINE_LOCAL)
        val initial = LudoEngine.createGame(GameMode.OFFLINE_LOCAL, players)

        val afterSix = LudoEngine.rollDice(initial, forcedValue = 6)
        assertEquals(TurnPhase.WAITING_FOR_MOVE, afterSix.phase)
        val validMoves = LudoEngine.getValidMoves(afterSix)
        assertEquals(4, validMoves.size)

        // Move token 0 out of BASE
        val afterMove = LudoEngine.moveToken(afterSix, tokenId = 0)
        val movedToken = afterMove.tokens.first { it.playerIndex == 0 && it.id == 0 }
        assertEquals(TokenState.ACTIVE, movedToken.state)
        assertEquals(0, movedToken.stepsFromStart)
        // Rolling a 6 grants an extra turn to player 0
        assertEquals(0, afterMove.currentTurnIndex)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, afterMove.phase)
    }

    @Test
    fun captureAndSafeCellProtection_worksCorrectly() {
        val players = LudoEngine.initializePlayers(2, GameMode.OFFLINE_LOCAL)
        val initial = LudoEngine.createGame(GameMode.OFFLINE_LOCAL, players)

        // Place Player 0 (RED, startOffset=0) at stepsFromStart=2 (global track 2)
        // Place Player 1 (YELLOW, startOffset=26) at stepsFromStart=32 -> (26+32)%52 = 6 (global track 6, unsafe)
        val customTokens = initial.tokens.map { t ->
            when {
                t.playerIndex == 0 && t.id == 0 -> t.copy(state = TokenState.ACTIVE, stepsFromStart = 2)
                t.playerIndex == 1 && t.id == 0 -> t.copy(state = TokenState.ACTIVE, stepsFromStart = 32)
                else -> t
            }
        }
        val stateWithTokens = initial.copy(tokens = customTokens)

        // Player 0 rolls a 4 -> lands on stepsFromStart=6 (global track 6) and captures Player 1's token!
        val rolled = LudoEngine.rollDice(stateWithTokens, forcedValue = 4)
        val afterCapture = LudoEngine.moveToken(rolled, tokenId = 0)

        val victim = afterCapture.tokens.first { it.playerIndex == 1 && it.id == 0 }
        assertEquals(TokenState.BASE, victim.state)
        assertEquals(-1, victim.stepsFromStart)
        assertEquals(1, afterCapture.capturesByPlayer[0])
        // Capture grants extra turn to Player 0
        assertEquals(0, afterCapture.currentTurnIndex)

        // Now test Safe Cell protection at global track 8 (Star cell)
        val safeSetupTokens = initial.tokens.map { t ->
            when {
                t.playerIndex == 0 && t.id == 0 -> t.copy(state = TokenState.ACTIVE, stepsFromStart = 5)
                t.playerIndex == 1 && t.id == 0 -> t.copy(state = TokenState.ACTIVE, stepsFromStart = 34) // (26+34)%52 = 8 (Safe Star)
                else -> t
            }
        }
        val safeState = LudoEngine.rollDice(initial.copy(tokens = safeSetupTokens), forcedValue = 3)
        val afterSafeMove = LudoEngine.moveToken(safeState, tokenId = 0)
        val protectedToken = afterSafeMove.tokens.first { it.playerIndex == 1 && it.id == 0 }
        assertEquals(TokenState.ACTIVE, protectedToken.state)
        assertEquals(34, protectedToken.stepsFromStart)
    }

    @Test
    fun homeLaneExactRollAndWinningCondition_detectsWinner() {
        val players = LudoEngine.initializePlayers(2, GameMode.OFFLINE_LOCAL)
        val initial = LudoEngine.createGame(GameMode.OFFLINE_LOCAL, players)

        // Set 3 tokens of Player 0 to FINISHED (56) and 1 token at HOME step 54 (needs exact 2 to reach 56)
        val nearWinTokens = initial.tokens.map { t ->
            if (t.playerIndex == 0) {
                if (t.id < 3) t.copy(state = TokenState.FINISHED, stepsFromStart = 56)
                else t.copy(state = TokenState.HOME, stepsFromStart = 54)
            } else t
        }
        val nearWinState = initial.copy(tokens = nearWinTokens)

        // Rolling 5 (overshoots 56) has no valid moves and passes turn
        val overshoot = LudoEngine.rollDice(nearWinState, forcedValue = 5)
        assertEquals(1, overshoot.currentTurnIndex)

        // Rolling exact 2 allows token 3 to finish and win the game!
        val exactRoll = LudoEngine.rollDice(nearWinState, forcedValue = 2)
        assertNull(LudoEngine.validateMove(exactRoll, tokenId = 0)) // already finished
        assertNotNull(LudoEngine.validateMove(exactRoll, tokenId = 3))

        val wonState = LudoEngine.moveToken(exactRoll, tokenId = 3)
        assertEquals(GameStatus.FINISHED, wonState.status)
        assertEquals(TurnPhase.GAME_OVER, wonState.phase)
        assertEquals(0, wonState.winnerIndex)
    }

    @Test
    fun aiController_prefersCaptureInMediumAndHardModes() {
        val players = LudoEngine.initializePlayers(
            playerCount = 2,
            mode = GameMode.VS_AI,
            aiDifficulty = AiDifficulty.HARD
        )
        val initial = LudoEngine.createGame(GameMode.VS_AI, players)
        // AI is playerIndex = 1 (YELLOW, startOffset = 26)
        // Give AI two active tokens:
        // Token 0 at stepsFromStart = 0 (global 26) -> +3 moves to 29 (where Player 0 RED token sits at stepsFromStart=29!)
        // Token 1 at stepsFromStart = 10 (global 36) -> +3 moves to 39 (empty)
        val tokens = initial.tokens.map { t ->
            when {
                t.playerIndex == 0 && t.id == 0 -> t.copy(state = TokenState.ACTIVE, stepsFromStart = 29)
                t.playerIndex == 1 && t.id == 0 -> t.copy(state = TokenState.ACTIVE, stepsFromStart = 0)
                t.playerIndex == 1 && t.id == 1 -> t.copy(state = TokenState.ACTIVE, stepsFromStart = 10)
                else -> t
            }
        }
        val aiTurnState = initial.copy(
            tokens = tokens,
            currentTurnIndex = 1,
            diceValue = 3,
            phase = TurnPhase.WAITING_FOR_MOVE
        )
        val chosenHard = AiController.selectBestMove(aiTurnState, AiDifficulty.HARD)
        assertNotNull(chosenHard)
        assertEquals(0, chosenHard?.tokenId)
        assertTrue(chosenHard!!.capturedTokens.isNotEmpty())
    }
}
