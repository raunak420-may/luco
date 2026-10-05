package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.LudoEngine
import com.example.localization.LocalizedText
import com.example.model.GameMode
import com.example.model.GameResult
import com.example.model.GameState
import com.example.model.GameStatus
import com.example.model.Player
import com.example.model.PlayerColor
import com.example.model.TokenState
import com.example.model.TurnPhase
import com.example.ui.ConnectionStatus
import com.example.ui.components.AnimatedRoyalDice
import com.example.ui.components.LudoBoardCanvas
import com.example.ui.components.toComposeColor
import com.example.ui.theme.RoyalCardSurface
import com.example.ui.theme.RoyalCardVariant
import com.example.ui.theme.RoyalEmeraldGreen
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalNavyDark
import com.example.ui.theme.RoyalTextMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    gameState: GameState,
    gameResult: GameResult?,
    strings: LocalizedText,
    languageCode: String,
    reducedMotion: Boolean,
    roomCode: String?,
    connectionStatus: ConnectionStatus,
    currentUserId: String,
    onRollDice: () -> Unit,
    onSelectToken: (Int) -> Unit,
    onExitGame: () -> Unit
) {
    BackHandler {
        onExitGame()
    }

    val validMoves = if (gameState.phase == TurnPhase.WAITING_FOR_MOVE) {
        LudoEngine.getValidMoves(gameState, gameState.currentTurnIndex, gameState.diceValue)
    } else {
        emptyList()
    }

    val currentPlayer = gameState.currentPlayer
    val isMyTurnToRoll = gameState.status == GameStatus.PLAYING &&
        gameState.phase == TurnPhase.WAITING_FOR_ROLL &&
        !gameState.isRolling &&
        when (gameState.mode) {
            GameMode.OFFLINE_LOCAL -> true
            GameMode.VS_AI -> currentPlayer?.isAi == false
            GameMode.QUICK_MATCH, GameMode.PRIVATE_ROOM -> currentPlayer?.id == currentUserId
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onExitGame,
                    modifier = Modifier.testTag("game_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = RoyalGold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (languageCode == "hi") gameState.mode.labelHi else gameState.mode.labelEn,
                        style = MaterialTheme.typography.titleMedium,
                        color = RoyalGold
                    )
                    val subtitle = buildString {
                        if (!roomCode.isNullOrBlank()) append("Room: $roomCode • ")
                        append("State v${gameState.version}")
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalTextMuted,
                        fontSize = 11.sp
                    )
                }

                // Online / Offline Status Pill
                val isOnlineMode = gameState.mode == GameMode.QUICK_MATCH || gameState.mode == GameMode.PRIVATE_ROOM
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RoyalCardSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (!isOnlineMode) RoyalGold.copy(alpha = 0.5f)
                        else if (connectionStatus == ConnectionStatus.ONLINE) RoyalEmeraldGreen
                        else RoyalGold
                    )
                ) {
                    Text(
                        text = when {
                            !isOnlineMode -> "OFFLINE"
                            connectionStatus == ConnectionStatus.ONLINE -> "ONLINE"
                            connectionStatus == ConnectionStatus.RECONNECTING -> "SYNCING..."
                            else -> "OFFLINE"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Players Status Grid (Top)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gameState.players.forEach { player ->
                    val finishedCount = gameState.tokens.count {
                        it.playerIndex == player.index && it.state == TokenState.FINISHED
                    }
                    val captures = gameState.capturesByPlayer[player.index] ?: 0
                    val isCurrent = gameState.currentTurnIndex == player.index
                    PlayerStatusBadge(
                        player = player,
                        finishedCount = finishedCount,
                        captures = captures,
                        isCurrentTurn = isCurrent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Center Ludo Board Canvas
            LudoBoardCanvas(
                tokens = gameState.tokens,
                validMoves = validMoves,
                safeCellsEnabled = gameState.rules.safeCellsEnabled,
                reducedMotion = reducedMotion,
                onTokenSelected = onSelectToken,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Turn Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = RoyalCardSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    (currentPlayer?.color ?: PlayerColor.RED).toComposeColor().copy(alpha = 0.7f)
                )
            ) {
                Text(
                    text = if (languageCode == "hi") gameState.statusMessageHi else gameState.statusMessageEn,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("turn_status_banner"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }

            // Accessible Quick-Select Buttons for Valid Token Moves
            if (validMoves.isNotEmpty() && (currentPlayer?.isAi == false)) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    validMoves.forEach { move ->
                        val actionDesc = when {
                            move.fromState == TokenState.BASE -> "Enter Board (Token #${move.tokenId + 1})"
                            move.capturedTokens.isNotEmpty() -> "Capture! (Token #${move.tokenId + 1})"
                            move.isHomeFinish -> "Finish Crown! (Token #${move.tokenId + 1})"
                            else -> "Move Token #${move.tokenId + 1} (+${gameState.diceValue})"
                        }
                        FilledTonalButton(
                            onClick = { onSelectToken(move.tokenId) },
                            modifier = Modifier.testTag("move_token_button_${move.tokenId}"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = RoyalCardVariant,
                                contentColor = RoyalGold
                            )
                        ) {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = actionDesc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Dice Control Bar
            AnimatedRoyalDice(
                diceValue = gameState.diceValue,
                isRolling = gameState.isRolling,
                canRoll = isMyTurnToRoll,
                activeColor = currentPlayer?.color ?: PlayerColor.RED,
                rollLabel = strings.rollDiceLabel,
                rollingLabel = strings.rollingLabel,
                tapTokenLabel = strings.tapTokenPrompt,
                isWaitingForMove = gameState.phase == TurnPhase.WAITING_FOR_MOVE,
                reducedMotion = reducedMotion,
                onRollClicked = onRollDice,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (gameState.status == GameStatus.FINISHED || gameResult != null) {
        val winnerName = gameResult?.winnerPlayer?.name
            ?: gameState.players.getOrNull(gameState.winnerIndex ?: 0)?.name
            ?: "Royal Monarch"

        AlertDialog(
            onDismissRequest = onExitGame,
            containerColor = RoyalCardSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = RoyalGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ROYAL VICTORY!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = RoyalGold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "👑 $winnerName conquered the board!",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (gameResult != null) {
                        Text(
                            text = "• Virtual Coins Earned: +${gameResult.coinsEarned}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoyalGold
                        )
                        Text(
                            text = "• Royal XP Awarded: +${gameResult.xpEarned} XP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoyalEmeraldGreen
                        )
                        Text(
                            text = "• Match Duration: ${gameResult.durationSeconds}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoyalTextMuted
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onExitGame,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalGold,
                        contentColor = RoyalNavyDark
                    ),
                    modifier = Modifier.testTag("victory_return_home_button")
                ) {
                    Text("Return to Palace", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun PlayerStatusBadge(
    player: Player,
    finishedCount: Int,
    captures: Int,
    isCurrentTurn: Boolean,
    modifier: Modifier = Modifier
) {
    val color = player.color.toComposeColor()
    Card(
        modifier = modifier
            .border(
                width = if (isCurrentTurn) 2.dp else 1.dp,
                color = if (isCurrentTurn) RoyalGold else color.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTurn) RoyalCardVariant else RoyalCardSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrentTurn) RoyalGold else Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "👑 $finishedCount/4  ⚔️ $captures",
                style = MaterialTheme.typography.bodyMedium,
                color = RoyalTextMuted,
                fontSize = 10.sp
            )
        }
    }
}
