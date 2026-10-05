package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.localization.LocalizedText
import com.example.model.AiDifficulty
import com.example.model.GameState
import com.example.model.OnlineRoom
import com.example.model.UserProfile
import com.example.ui.AppScreen
import com.example.ui.ConnectionStatus
import com.example.ui.theme.RoyalAmberYellow
import com.example.ui.theme.RoyalCardSurface
import com.example.ui.theme.RoyalCardVariant
import com.example.ui.theme.RoyalEmeraldGreen
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalNavyDark
import com.example.ui.theme.RoyalRubyRed
import com.example.ui.theme.RoyalSapphireBlue
import com.example.ui.theme.RoyalTextMuted

val ROYAL_AVATAR_EMOJIS = listOf(
    "👑", "🦁", "🦅", "🐉", "⚔️", "🛡️", "💎", "⚜️", "🏰", "🌟", "🔥", "⚡"
)

@Composable
fun HomeScreen(
    profile: UserProfile?,
    strings: LocalizedText,
    savedOfflineGame: GameState?,
    reconnectableRoom: OnlineRoom?,
    connectionStatus: ConnectionStatus,
    onStartQuickMatch: () -> Unit,
    onStartAiMatch: (Int, AiDifficulty) -> Unit,
    onStartOfflineLocal: (Int) -> Unit,
    onOpenLobby: () -> Unit,
    onResumeSavedGame: () -> Unit,
    onReconnectRoom: (OnlineRoom) -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    var showAiDialog by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
            // Top Player Profile, Coins, Level & Connection Bar
            TopProfileStatusBar(
                profile = profile,
                strings = strings,
                connectionStatus = connectionStatus,
                onProfileClick = { onNavigate(AppScreen.PROFILE) },
                onSettingsClick = { onNavigate(AppScreen.SETTINGS) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Banner Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.dp, RoyalGold, RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_royal_ludo_banner_1791227671597),
                    contentDescription = "Royal Dice Ludo Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    RoyalNavyDark.copy(alpha = 0.88f)
                                )
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(
                            text = strings.appTitle.uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = RoyalGold
                        )
                        Text(
                            text = strings.tagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // Reconnect Active Online Room Banner (if any)
            if (reconnectableRoom != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, RoyalEmeraldGreen, RoundedCornerShape(16.dp))
                        .clickable { onReconnectRoom(reconnectableRoom) }
                        .testTag("reconnect_room_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E3A2F))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = RoyalEmeraldGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Online Match Found (Room ${reconnectableRoom.roomCode})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Tap to reconnect & restore authoritative board state (v${reconnectableRoom.version})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoyalTextMuted
                            )
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = "Reconnect", tint = RoyalGold)
                    }
                }
            }

            // Resume Saved Offline Match Banner (if any)
            if (savedOfflineGame != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, RoyalGold, RoundedCornerShape(16.dp))
                        .clickable { onResumeSavedGame() }
                        .testTag("resume_saved_game_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RoyalCardVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = RoyalGold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.resumeSavedGame,
                                style = MaterialTheme.typography.titleMedium,
                                color = RoyalGold
                            )
                            Text(
                                text = "${savedOfflineGame.mode.labelEn} • ${savedOfflineGame.players.size} Players • Turn: ${savedOfflineGame.currentPlayer?.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4 Primary Game Mode Cards (2x2 Grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameModeCard(
                    title = strings.quickMatchTitle,
                    subtitle = strings.quickMatchSub,
                    icon = Icons.Default.Bolt,
                    accentColor = RoyalRubyRed,
                    testTag = "mode_quick_match",
                    onClick = onStartQuickMatch,
                    modifier = Modifier.weight(1f)
                )
                GameModeCard(
                    title = strings.playWithAiTitle,
                    subtitle = strings.playWithAiSub,
                    icon = Icons.Default.SmartToy,
                    accentColor = RoyalSapphireBlue,
                    testTag = "mode_play_ai",
                    onClick = { showAiDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameModeCard(
                    title = strings.playOfflineTitle,
                    subtitle = strings.playOfflineSub,
                    icon = Icons.Default.Group,
                    accentColor = RoyalEmeraldGreen,
                    testTag = "mode_play_offline",
                    onClick = { showOfflineDialog = true },
                    modifier = Modifier.weight(1f)
                )
                GameModeCard(
                    title = strings.privateRoomTitle,
                    subtitle = strings.privateRoomSub,
                    icon = Icons.Default.MeetingRoom,
                    accentColor = RoyalAmberYellow,
                    testTag = "mode_private_room",
                    onClick = onOpenLobby,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Access Feature Strip (Leaderboard, Achievements, Match History, Settings)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionPill(
                    label = strings.navLeaderboard,
                    icon = Icons.Default.Leaderboard,
                    tag = "quick_leaderboard",
                    onClick = { onNavigate(AppScreen.LEADERBOARD) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionPill(
                    label = strings.navAchievements,
                    icon = Icons.Default.EmojiEvents,
                    tag = "quick_achievements",
                    onClick = { onNavigate(AppScreen.ACHIEVEMENTS) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionPill(
                    label = strings.navHistory,
                    icon = Icons.Default.History,
                    tag = "quick_history",
                    onClick = { onNavigate(AppScreen.HISTORY) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = strings.virtualCoinDisclaimer,
                style = MaterialTheme.typography.bodyMedium,
                color = RoyalTextMuted.copy(alpha = 0.75f),
                fontSize = 11.sp
            )
        }
    }

    if (showAiDialog) {
        AiSetupDialog(
            onDismiss = { showAiDialog = false },
            onStart = { players, diff ->
                showAiDialog = false
                onStartAiMatch(players, diff)
            }
        )
    }

    if (showOfflineDialog) {
        OfflinePassPlayDialog(
            onDismiss = { showOfflineDialog = false },
            onStart = { players ->
                showOfflineDialog = false
                onStartOfflineLocal(players)
            }
        )
    }
}

@Composable
private fun TopProfileStatusBar(
    profile: UserProfile?,
    strings: LocalizedText,
    connectionStatus: ConnectionStatus,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val avatarEmoji = ROYAL_AVATAR_EMOJIS.getOrElse(profile?.avatarIndex ?: 0) { "👑" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, RoyalGold.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar & Username
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onProfileClick() }
                    .padding(4.dp)
                    .testTag("home_profile_header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(RoyalCardVariant)
                        .border(2.dp, RoyalGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = avatarEmoji, fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile?.username ?: "RoyalMonarch",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionStatus) {
                                        ConnectionStatus.ONLINE -> RoyalEmeraldGreen
                                        ConnectionStatus.RECONNECTING -> RoyalAmberYellow
                                        ConnectionStatus.OFFLINE -> RoyalRubyRed
                                    }
                                )
                        )
                    }
                    Text(
                        text = "${strings.levelLabel} ${profile?.level ?: 1} • ${profile?.xp ?: 0} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalGold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { profile?.levelProgress ?: 0.1f },
                        modifier = Modifier
                            .width(110.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = RoyalGold,
                        trackColor = RoyalNavyDark
                    )
                }
            }

            // Virtual Coins Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = RoyalNavyDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = RoyalGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${profile?.coins ?: 1000}",
                        style = MaterialTheme.typography.labelLarge,
                        color = RoyalGold
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("home_settings_button")
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun GameModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(138.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .border(1.5.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.25f),
                            RoyalCardSurface
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.25f))
                    .border(1.dp, accentColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(24.dp))
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoyalTextMuted,
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = RoyalCardVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AiSetupDialog(
    onDismiss: () -> Unit,
    onStart: (Int, AiDifficulty) -> Unit
) {
    var selectedPlayers by remember { mutableIntStateOf(2) }
    var selectedDifficulty by remember { mutableStateOf(AiDifficulty.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RoyalCardSurface,
        title = {
            Text("Play vs Royal AI", style = MaterialTheme.typography.titleLarge, color = RoyalGold)
        },
        text = {
            Column {
                Text("Select AI Intelligence:", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiDifficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = selectedDifficulty == diff,
                            onClick = { selectedDifficulty = diff },
                            label = { Text(diff.labelEn) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text("Total Players on Board:", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4).forEach { count ->
                        FilterChip(
                            selected = selectedPlayers == count,
                            onClick = { selectedPlayers = count },
                            label = { Text("$count Players") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(selectedPlayers, selectedDifficulty) },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = RoyalNavyDark),
                modifier = Modifier.testTag("confirm_start_ai_button")
            ) {
                Text("Start Battle", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RoyalTextMuted)
            }
        }
    )
}

@Composable
private fun OfflinePassPlayDialog(
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit
) {
    var selectedPlayers by remember { mutableIntStateOf(2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RoyalCardSurface,
        title = {
            Text("Offline Pass & Play", style = MaterialTheme.typography.titleLarge, color = RoyalGold)
        },
        text = {
            Column {
                Text(
                    "Choose number of human players on this device:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4).forEach { count ->
                        FilterChip(
                            selected = selectedPlayers == count,
                            onClick = { selectedPlayers = count },
                            label = { Text("$count Players") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(selectedPlayers) },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = RoyalNavyDark),
                modifier = Modifier.testTag("confirm_start_offline_button")
            ) {
                Text("Start Match", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RoyalTextMuted)
            }
        }
    )
}
