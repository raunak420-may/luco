package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.LudoEngine
import com.example.model.GameMode
import com.example.model.GameState
import com.example.model.GameStatus
import com.example.model.TokenState
import com.example.model.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readAppNameAndSaveRestoreCompactGameState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Royal Dice Ludo", appName)

        // Verify compact JSON serialization & restoration for offline save/restore and online sync
        val players = LudoEngine.initializePlayers(4, GameMode.OFFLINE_LOCAL)
        val game = LudoEngine.createGame(GameMode.OFFLINE_LOCAL, players)
        val rolled = LudoEngine.rollDice(game, forcedValue = 6)
        val moved = LudoEngine.moveToken(rolled, tokenId = 0)

        val json = moved.toCompactBoardJson()
        val restored = GameState.fromCompactBoardJson(
            gameId = moved.gameId,
            version = moved.version,
            mode = moved.mode,
            status = moved.status,
            currentTurnIndex = moved.currentTurnIndex,
            diceValue = moved.diceValue,
            phase = moved.phase,
            winnerUid = "",
            lastActionId = moved.lastActionId,
            jsonStr = json
        )

        assertEquals(moved.gameId, restored.gameId)
        assertEquals(4, restored.players.size)
        val token0 = restored.tokens.first { it.playerIndex == 0 && it.id == 0 }
        assertEquals(TokenState.ACTIVE, token0.state)
        assertEquals(0, token0.stepsFromStart)
        assertEquals(GameStatus.PLAYING, restored.status)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, restored.phase)
    }
}
