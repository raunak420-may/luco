package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettings
import com.example.data.local.LocalMatchHistoryEntity
import com.example.localization.LocalizedText
import com.example.model.AchievementCatalog
import com.example.model.LeaderboardEntry
import com.example.model.MatchRecord
import com.example.model.OnlineRoom
import com.example.model.UserProfile
import com.example.ui.theme.RoyalAmberYellow
import com.example.ui.theme.RoyalCardSurface
import com.example.ui.theme.RoyalCardVariant
import com.example.ui.theme.RoyalEmeraldGreen
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalNavyDark
import com.example.ui.theme.RoyalRubyRed
import com.example.ui.theme.RoyalTextMuted

// 1. PRIVATE ROOM & ONLINE LOBBY SCREEN
@Composable
fun LobbyScreen(
    activeRoom: OnlineRoom?,
    strings: LocalizedText,
    isBusy: Boolean,
    onCreateRoom: (Int) -> Unit,
    onJoinByCode: (String) -> Unit,
    onHostStartMatch: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var roomCodeInput by remember { mutableStateOf("") }
    var selectedMaxPlayers by remember { mutableIntStateOf(2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            ScreenHeader(title = strings.privateRoomTitle, onBack = onBack)

            Spacer(modifier = Modifier.height(14.dp))

            if (activeRoom != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, RoyalGold, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ROOM CODE",
                            style = MaterialTheme.typography.labelLarge,
                            color = RoyalTextMuted
                        )
                        Text(
                            text = activeRoom.roomCode,
                            style = MaterialTheme.typography.displayLarge,
                            color = RoyalGold,
                            modifier = Modifier.testTag("active_room_code_text")
                        )
                        Text(
                            text = "Status: ${activeRoom.status} • Players: ${activeRoom.playerUids.size}/${activeRoom.maxPlayers}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        activeRoom.playerNames.forEachIndexed { idx, pName ->
                            val uid = activeRoom.playerUids.getOrNull(idx) ?: ""
                            val isHost = uid == activeRoom.hostUid
                            val isReady = activeRoom.readyUids.contains(uid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RoyalCardVariant)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${idx + 1}. $pName ${if (isHost) "(Host 👑)" else ""}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isReady) "READY ✓" else "WAITING",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isReady) RoyalEmeraldGreen else RoyalAmberYellow
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onHostStartMatch,
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_online_room_match_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalGold,
                                contentColor = RoyalNavyDark
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Online Match Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Create New Private Room Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.createRoomBtn,
                        style = MaterialTheme.typography.titleLarge,
                        color = RoyalGold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4).forEach { count ->
                            FilterChip(
                                selected = selectedMaxPlayers == count,
                                onClick = { selectedMaxPlayers = count },
                                label = { Text("$count Players") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onCreateRoom(selectedMaxPlayers) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_private_room_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalGold,
                            contentColor = RoyalNavyDark
                        )
                    ) {
                        Icon(Icons.Default.MeetingRoom, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.createRoomBtn, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Join Room by Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.joinRoomBtn,
                        style = MaterialTheme.typography.titleLarge,
                        color = RoyalGold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = { roomCodeInput = it.uppercase().take(8) },
                        label = { Text(strings.enterRoomCodeHint) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_code_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onJoinByCode(roomCodeInput) },
                        enabled = !isBusy && roomCodeInput.length >= 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("join_private_room_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalEmeraldGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(strings.joinRoomBtn, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 2. LEADERBOARD SCREEN
@Composable
fun LeaderboardScreen(
    entries: List<LeaderboardEntry>,
    currentUserId: String,
    strings: LocalizedText,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var selectedTab by remember { mutableStateOf("Global") }
    var sortBy by remember { mutableStateOf("Wins") }

    val sortedEntries = remember(entries, sortBy) {
        when (sortBy) {
            "XP" -> entries.sortedByDescending { it.xp }
            "Level" -> entries.sortedByDescending { it.level * 10000 + it.xp }
            "Win Rate" -> entries.sortedByDescending { it.winRatePercent * 1000 + it.wins }
            else -> entries.sortedByDescending { it.wins * 10000 + it.xp }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp)) {
            ScreenHeader(title = strings.navLeaderboard, onBack = onBack)

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Global", "Weekly", "Monthly").forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Wins", "XP", "Level", "Win Rate").forEach { criterion ->
                    FilterChip(
                        selected = sortBy == criterion,
                        onClick = { sortBy = criterion },
                        label = { Text("By $criterion", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (sortedEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Complete your first match to appear on the Royal Leaderboard!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = RoyalTextMuted
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(sortedEntries, key = { _, item -> item.userId }) { idx, entry ->
                        val isMe = entry.userId == currentUserId
                        val avatar = ROYAL_AVATAR_EMOJIS.getOrElse(entry.avatarIndex) { "👑" }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isMe) 2.dp else 1.dp,
                                    color = if (isMe) RoyalGold else Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMe) RoyalCardVariant else RoyalCardSurface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (idx < 3) RoyalGold else Color.White,
                                    modifier = Modifier.width(40.dp)
                                )
                                Text(text = avatar, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.username + if (isMe) " (You)" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Lv.${entry.level} • ${entry.xp} XP • Win Rate ${entry.winRatePercent}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RoyalTextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = "${entry.wins} W",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = RoyalGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 3. ACHIEVEMENTS SCREEN
@Composable
fun AchievementsScreen(
    profile: UserProfile?,
    strings: LocalizedText,
    languageCode: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val unlockedIds = profile?.unlockedAchievements?.toSet() ?: emptySet()
    val safeProfile = profile ?: UserProfile()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp)) {
            ScreenHeader(title = strings.navAchievements, onBack = onBack)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(AchievementCatalog.all, key = { it.id }) { ach ->
                    val isUnlocked = ach.id in unlockedIds
                    val currentProgress = ach.progressExtractor(safeProfile, 0).coerceAtMost(ach.targetValue)
                    val ratio = (currentProgress.toFloat() / ach.targetValue.toFloat()).coerceIn(0f, 1f)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = if (isUnlocked) RoyalGold else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUnlocked) RoyalCardVariant else RoyalCardSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isUnlocked) RoyalGold else RoyalTextMuted,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (languageCode == "hi") ach.titleHi else ach.titleEn,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isUnlocked) RoyalGold else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (languageCode == "hi") ach.descriptionHi else ach.descriptionEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RoyalTextMuted
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { if (isUnlocked) 1f else ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (isUnlocked) RoyalEmeraldGreen else RoyalGold,
                                    trackColor = RoyalNavyDark
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+${ach.rewardCoins} 🪙",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RoyalGold
                                )
                                Text(
                                    text = "+${ach.rewardXp} XP",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RoyalEmeraldGreen,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. MATCH HISTORY SCREEN
@Composable
fun HistoryScreen(
    cloudHistory: List<MatchRecord>,
    localHistory: List<LocalMatchHistoryEntity>,
    strings: LocalizedText,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp)) {
            ScreenHeader(title = strings.navHistory, onBack = onBack)
            Spacer(modifier = Modifier.height(12.dp))

            if (cloudHistory.isEmpty() && localHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No completed matches yet. Roll the dice and conquer!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = RoyalTextMuted
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (cloudHistory.isNotEmpty()) {
                        items(cloudHistory, key = { it.matchId }) { item ->
                            MatchHistoryCard(
                                mode = item.gameMode,
                                opponents = item.opponentNames,
                                winner = item.winnerName,
                                isWin = item.isWin,
                                durationSeconds = item.durationSeconds,
                                coinsDelta = item.coinsDelta,
                                xpEarned = item.xpEarned
                            )
                        }
                    } else {
                        items(localHistory, key = { it.matchId }) { item ->
                            MatchHistoryCard(
                                mode = item.gameMode,
                                opponents = item.opponentNames,
                                winner = item.winnerName,
                                isWin = item.isWin,
                                durationSeconds = item.durationSeconds,
                                coinsDelta = item.coinsDelta,
                                xpEarned = item.xpEarned
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchHistoryCard(
    mode: String,
    opponents: String,
    winner: String,
    isWin: Boolean,
    durationSeconds: Int,
    coinsDelta: Int,
    xpEarned: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isWin) RoyalEmeraldGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isWin) "VICTORY • $mode" else "MATCH COMPLETED • $mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isWin) RoyalEmeraldGreen else RoyalGold,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Winner: $winner • Opponents: $opponents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = "Duration: ${durationSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoyalTextMuted,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+$coinsDelta 🪙",
                    style = MaterialTheme.typography.labelLarge,
                    color = RoyalGold
                )
                Text(
                    text = "+$xpEarned XP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoyalEmeraldGreen,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// 5. PROFILE SCREEN
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    strings: LocalizedText,
    onSaveProfile: (String, Int) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val safeProfile = profile ?: UserProfile()
    var usernameInput by remember(safeProfile.username) { mutableStateOf(safeProfile.username) }
    var selectedAvatar by remember(safeProfile.avatarIndex) { mutableIntStateOf(safeProfile.avatarIndex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            ScreenHeader(title = strings.navProfile, onBack = onBack)
            Spacer(modifier = Modifier.height(14.dp))

            // Main Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, RoyalGold, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(RoyalCardVariant)
                            .border(2.5.dp, RoyalGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ROYAL_AVATAR_EMOJIS.getOrElse(selectedAvatar) { "👑" },
                            fontSize = 36.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = safeProfile.username,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Player ID: ${safeProfile.userId.take(12)} • Level ${safeProfile.level}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalTextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn("Played", "${safeProfile.gamesPlayed}")
                        StatColumn("Wins", "${safeProfile.wins}")
                        StatColumn("Win Rate", "${safeProfile.winRatePercent}%")
                        StatColumn("Best Streak", "${safeProfile.bestStreak}")
                        StatColumn("Captures", "${safeProfile.capturesTotal}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Customize Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Customize Royal Identity",
                        style = MaterialTheme.typography.titleLarge,
                        color = RoyalGold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it.take(30) },
                        label = { Text("Display Name (2-30 chars)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_username_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Choose Royal Crest Avatar:", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ROYAL_AVATAR_EMOJIS.take(6).forEachIndexed { idx, emoji ->
                            AvatarChoiceBubble(
                                emoji = emoji,
                                isSelected = selectedAvatar == idx,
                                onClick = { selectedAvatar = idx }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ROYAL_AVATAR_EMOJIS.drop(6).take(6).forEachIndexed { offset, emoji ->
                            val idx = offset + 6
                            AvatarChoiceBubble(
                                emoji = emoji,
                                isSelected = selectedAvatar == idx,
                                onClick = { selectedAvatar = idx }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onSaveProfile(usernameInput, selectedAvatar) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_profile_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalGold,
                            contentColor = RoyalNavyDark
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RoyalRubyRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out of Google Account", color = RoyalRubyRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = RoyalGold)
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = RoyalTextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun AvatarChoiceBubble(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) RoyalCardVariant else RoyalNavyDark)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) RoyalGold else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 22.sp)
    }
}

// 6. SETTINGS SCREEN
@Composable
fun SettingsScreen(
    settings: AppSettings,
    strings: LocalizedText,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            ScreenHeader(title = strings.navSettings, onBack = onBack)
            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalCardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Audio, Haptics & Motion", style = MaterialTheme.typography.titleLarge, color = RoyalGold)
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Sound Effects (Dice, Moves, Fanfare)",
                        checked = settings.isSoundEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(isSoundEnabled = v) } }
                    )
                    SettingToggleRow(
                        title = "Ambient Palace Music",
                        checked = settings.isMusicEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(isMusicEnabled = v) } }
                    )
                    SettingToggleRow(
                        title = "Haptic Vibration Feedback",
                        checked = settings.isVibrationEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(isVibrationEnabled = v) } }
                    )
                    SettingToggleRow(
                        title = "Reduced Motion (Low-End Phone Optimization)",
                        checked = settings.isReducedMotion,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(isReducedMotion = v) } }
                    )
                    SettingToggleRow(
                        title = "Game Notifications",
                        checked = settings.isNotificationsEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(isNotificationsEnabled = v) } }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.12f))

                    Text("Board Rules & Language", style = MaterialTheme.typography.titleLarge, color = RoyalGold)
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Exact Dice Roll Required for Final Home",
                        checked = settings.exactRollToFinish,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(exactRollToFinish = v) } }
                    )
                    SettingToggleRow(
                        title = "Star Safe Cells Protect Tokens from Capture",
                        checked = settings.safeCellsEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(safeCellsEnabled = v) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Language / भाषा:", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = settings.languageCode == "en",
                            onClick = { onUpdateSettings { it.copy(languageCode = "en") } },
                            label = { Text("English") },
                            modifier = Modifier.testTag("lang_en_chip")
                        )
                        FilterChip(
                            selected = settings.languageCode == "hi",
                            onClick = { onUpdateSettings { it.copy(languageCode = "hi") } },
                            label = { Text("हिन्दी (Hindi)") },
                            modifier = Modifier.testTag("lang_hi_chip")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.12f))

                    Text("About & Fair Play Policy", style = MaterialTheme.typography.titleMedium, color = RoyalGold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Royal Dice Ludo v1.0 (com.aistudio.royaldiceludo.rxludo)\n" +
                            "• 100% Virtual Casual Board Game — No real-money gambling, betting, cash prizes, or withdrawals.\n" +
                            "• Authoritative anti-cheat state verification & Google Sign-In security.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalTextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("subscreen_back_button")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = RoyalGold
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = RoyalGold
        )
    }
}
