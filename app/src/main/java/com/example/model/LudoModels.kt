package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.json.JSONArray
import org.json.JSONObject

enum class PlayerColor(
    val displayNameEn: String,
    val displayNameHi: String,
    val startOffset: Int
) {
    RED("Ruby Red", "रूबी लाल", 0),
    GREEN("Emerald Green", "पन्ना हरा", 13),
    YELLOW("Amber Gold", "अंबर स्वर्ण", 26),
    BLUE("Sapphire Blue", "नीलम नीला", 39)
}

enum class TokenState {
    BASE,
    ACTIVE,
    HOME,
    FINISHED
}

enum class AiDifficulty(val labelEn: String, val labelHi: String) {
    EASY("Easy", "आसान"),
    MEDIUM("Medium", "मध्यम"),
    HARD("Hard", "कठिन")
}

enum class GameMode(val labelEn: String, val labelHi: String) {
    OFFLINE_LOCAL("Local Pass & Play", "लोकल पास और प्ले"),
    VS_AI("Play vs Royal AI", "शाही AI के विरुद्ध"),
    QUICK_MATCH("Online Quick Match", "ऑनलाइन क्विक मैच"),
    PRIVATE_ROOM("Private Room", "प्राइवेट रूम")
}

enum class TurnPhase {
    WAITING_FOR_ROLL,
    WAITING_FOR_MOVE,
    GAME_OVER
}

enum class GameStatus {
    WAITING,
    PLAYING,
    FINISHED
}

data class GameRules(
    val entryDiceValues: Set<Int> = setOf(6),
    val extraTurnOnSix: Boolean = true,
    val extraTurnOnCapture: Boolean = true,
    val extraTurnOnHomeFinish: Boolean = true,
    val maxConsecutiveSixes: Int = 3,
    val safeCellsEnabled: Boolean = true,
    val exactRollToFinish: Boolean = true
)

data class Token(
    val id: Int = 0, // 0..3 within player
    val playerIndex: Int = 0,
    val color: PlayerColor = PlayerColor.RED,
    val state: TokenState = TokenState.BASE,
    val stepsFromStart: Int = -1 // -1 = BASE, 0..50 = MAIN TRACK, 51..55 = HOME LANE, 56 = FINISHED
) {
    val globalTrackIndex: Int?
        get() = if (state == TokenState.ACTIVE && stepsFromStart in 0..50) {
            (color.startOffset + stepsFromStart) % 52
        } else {
            null
        }
}

data class Player(
    val index: Int = 0,
    val id: String = "",
    val name: String = "",
    val color: PlayerColor = PlayerColor.RED,
    val isAi: Boolean = false,
    val aiDifficulty: AiDifficulty = AiDifficulty.MEDIUM,
    val avatarIndex: Int = 0,
    val isConnected: Boolean = true
)

data class Move(
    val tokenId: Int,
    val playerIndex: Int,
    val fromSteps: Int,
    val toSteps: Int,
    val fromState: TokenState,
    val toState: TokenState,
    val capturedTokens: List<Token> = emptyList(),
    val isHomeFinish: Boolean = false
)

data class GameAction(
    val actionId: String,
    val gameId: String,
    val actionType: String, // "ROLL_DICE" or "MOVE_TOKEN"
    val expectedVersion: Int,
    val playerId: String,
    val tokenId: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class GameResult(
    val gameId: String,
    val mode: GameMode,
    val winnerPlayer: Player,
    val players: List<Player>,
    val durationSeconds: Int,
    val coinsEarned: Int,
    val xpEarned: Int,
    val newlyUnlockedAchievements: List<String> = emptyList()
)

data class GameState(
    val gameId: String = "",
    val version: Int = 1,
    val mode: GameMode = GameMode.OFFLINE_LOCAL,
    val status: GameStatus = GameStatus.PLAYING,
    val rules: GameRules = GameRules(),
    val players: List<Player> = emptyList(),
    val tokens: List<Token> = emptyList(),
    val currentTurnIndex: Int = 0,
    val diceValue: Int = 0,
    val isRolling: Boolean = false,
    val phase: TurnPhase = TurnPhase.WAITING_FOR_ROLL,
    val consecutiveSixes: Int = 0,
    val lastMove: Move? = null,
    val winnerIndex: Int? = null,
    val capturesByPlayer: Map<Int, Int> = emptyMap(),
    val startedAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val lastActionId: String = "init",
    val statusMessageEn: String = "Tap the Royal Dice to roll!",
    val statusMessageHi: String = "पासा फेंकने के लिए टैप करें!"
) {
    val currentPlayer: Player?
        get() = players.getOrNull(currentTurnIndex)

    fun tokensForPlayer(playerIndex: Int): List<Token> =
        tokens.filter { it.playerIndex == playerIndex }

    fun toCompactBoardJson(): String {
        val root = JSONObject()
        root.put("consecutiveSixes", consecutiveSixes)
        root.put("startedAtMillis", startedAtMillis)
        root.put("statusMessageEn", statusMessageEn)
        root.put("statusMessageHi", statusMessageHi)
        val playersArr = JSONArray()
        players.forEach { p ->
            playersArr.put(
                JSONObject().apply {
                    put("index", p.index)
                    put("id", p.id)
                    put("name", p.name)
                    put("color", p.color.name)
                    put("isAi", p.isAi)
                    put("aiDifficulty", p.aiDifficulty.name)
                    put("avatarIndex", p.avatarIndex)
                }
            )
        }
        root.put("players", playersArr)

        val tokensArr = JSONArray()
        tokens.forEach { t ->
            tokensArr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("p", t.playerIndex)
                    put("c", t.color.name)
                    put("s", t.state.name)
                    put("st", t.stepsFromStart)
                }
            )
        }
        root.put("tokens", tokensArr)

        val capsObj = JSONObject()
        capturesByPlayer.forEach { (k, v) -> capsObj.put(k.toString(), v) }
        root.put("captures", capsObj)
        return root.toString()
    }

    companion object {
        fun fromCompactBoardJson(
            gameId: String,
            version: Int,
            mode: GameMode,
            status: GameStatus,
            currentTurnIndex: Int,
            diceValue: Int,
            phase: TurnPhase,
            winnerUid: String,
            lastActionId: String,
            jsonStr: String
        ): GameState {
            val obj = try {
                JSONObject(jsonStr)
            } catch (e: Exception) {
                JSONObject()
            }
            val playersList = mutableListOf<Player>()
            val playersArr = obj.optJSONArray("players")
            if (playersArr != null) {
                for (i in 0 until playersArr.length()) {
                    val pObj = playersArr.getJSONObject(i)
                    playersList.add(
                        Player(
                            index = pObj.optInt("index", i),
                            id = pObj.optString("id", ""),
                            name = pObj.optString("name", "Player ${i + 1}"),
                            color = runCatching { PlayerColor.valueOf(pObj.optString("color", "RED")) }
                                .getOrDefault(PlayerColor.entries[i % 4]),
                            isAi = pObj.optBoolean("isAi", false),
                            aiDifficulty = runCatching {
                                AiDifficulty.valueOf(pObj.optString("aiDifficulty", "MEDIUM"))
                            }.getOrDefault(AiDifficulty.MEDIUM),
                            avatarIndex = pObj.optInt("avatarIndex", i)
                        )
                    )
                }
            }

            val tokensList = mutableListOf<Token>()
            val tokensArr = obj.optJSONArray("tokens")
            if (tokensArr != null) {
                for (i in 0 until tokensArr.length()) {
                    val tObj = tokensArr.getJSONObject(i)
                    tokensList.add(
                        Token(
                            id = tObj.optInt("id", i % 4),
                            playerIndex = tObj.optInt("p", i / 4),
                            color = runCatching { PlayerColor.valueOf(tObj.optString("c", "RED")) }
                                .getOrDefault(PlayerColor.RED),
                            state = runCatching { TokenState.valueOf(tObj.optString("s", "BASE")) }
                                .getOrDefault(TokenState.BASE),
                            stepsFromStart = tObj.optInt("st", -1)
                        )
                    )
                }
            }

            val capturesMap = mutableMapOf<Int, Int>()
            val capsObj = obj.optJSONObject("captures")
            capsObj?.keys()?.forEach { key ->
                val idx = key.toIntOrNull()
                if (idx != null) {
                    capturesMap[idx] = capsObj.optInt(key, 0)
                }
            }

            val winnerIdx = if (winnerUid.isNotBlank()) {
                playersList.indexOfFirst { it.id == winnerUid }.takeIf { it >= 0 }
            } else null

            return GameState(
                gameId = gameId,
                version = version,
                mode = mode,
                status = status,
                players = playersList,
                tokens = tokensList,
                currentTurnIndex = currentTurnIndex,
                diceValue = diceValue,
                phase = phase,
                consecutiveSixes = obj.optInt("consecutiveSixes", 0),
                winnerIndex = winnerIdx,
                capturesByPlayer = capturesMap,
                startedAtMillis = obj.optLong("startedAtMillis", System.currentTimeMillis()),
                updatedAtMillis = System.currentTimeMillis(),
                lastActionId = lastActionId,
                statusMessageEn = obj.optString("statusMessageEn", "Tap the Royal Dice to roll!"),
                statusMessageHi = obj.optString("statusMessageHi", "पासा फेंकने के लिए टैप करें!")
            )
        }
    }
}

// Firestore Data Models (Every property has a default value for no-arg constructor)

data class UserProfile(
    val userId: String = "",
    val username: String = "RoyalMonarch",
    val avatarIndex: Int = 0,
    val level: Int = 1,
    val xp: Int = 0,
    val coins: Int = 1000,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val capturesTotal: Int = 0,
    val unlockedAchievements: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val winRatePercent: Int
        get() = if (gamesPlayed > 0) ((wins * 100) / gamesPlayed).coerceIn(0, 100) else 0

    val xpForNextLevel: Int
        get() = level * 250

    val levelProgress: Float
        get() {
            val prevLevelXp = (level - 1) * 250
            val currentSpan = (xp - prevLevelXp).coerceAtLeast(0)
            return (currentSpan.toFloat() / 250f).coerceIn(0f, 1f)
        }

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "username" to username.take(30).padEnd(2, 'X'),
        "avatarIndex" to avatarIndex.coerceIn(0, 11),
        "level" to level.coerceIn(1, 1000),
        "xp" to xp.coerceAtLeast(0),
        "coins" to coins.coerceAtLeast(0),
        "gamesPlayed" to gamesPlayed.coerceAtLeast(0),
        "wins" to wins.coerceAtLeast(0),
        "losses" to losses.coerceAtLeast(0),
        "currentStreak" to currentStreak.coerceAtLeast(0),
        "bestStreak" to bestStreak.coerceAtLeast(0),
        "capturesTotal" to capturesTotal.coerceAtLeast(0),
        "unlockedAchievements" to unlockedAchievements.take(30),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class LeaderboardEntry(
    val userId: String = "",
    val username: String = "RoyalMonarch",
    val avatarIndex: Int = 0,
    val wins: Int = 0,
    val xp: Int = 0,
    val level: Int = 1,
    val gamesPlayed: Int = 0,
    val winRatePercent: Int = 0,
    val isPublic: Boolean = true,
    val updatedAt: Timestamp? = null
) {
    fun toWriteMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "username" to username.take(30).padEnd(2, 'X'),
        "avatarIndex" to avatarIndex.coerceIn(0, 11),
        "wins" to wins.coerceAtLeast(0),
        "xp" to xp.coerceAtLeast(0),
        "level" to level.coerceIn(1, 1000),
        "gamesPlayed" to gamesPlayed.coerceAtLeast(0),
        "winRatePercent" to winRatePercent.coerceIn(0, 100),
        "isPublic" to true,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class OnlineRoom(
    val roomId: String = "",
    val roomCode: String = "",
    val hostUid: String = "",
    val hostName: String = "Host",
    val mode: String = "PRIVATE_ROOM",
    val status: String = "WAITING",
    val maxPlayers: Int = 2,
    val playerUids: List<String> = emptyList(),
    val playerNames: List<String> = emptyList(),
    val readyUids: List<String> = emptyList(),
    val currentTurnIndex: Int = 0,
    val currentTurnUid: String = "",
    val diceValue: Int = 0,
    val phase: String = "WAITING_FOR_ROLL",
    val version: Int = 1,
    val boardStateJson: String = "{}",
    val lastActionId: String = "init",
    val winnerUid: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "roomId" to roomId,
        "roomCode" to roomCode,
        "hostUid" to hostUid,
        "hostName" to hostName.take(30).padEnd(2, 'P'),
        "mode" to mode,
        "status" to status,
        "maxPlayers" to maxPlayers.coerceIn(2, 4),
        "playerUids" to playerUids.take(4),
        "playerNames" to playerNames.map { it.take(30) }.take(4),
        "readyUids" to readyUids.take(4),
        "currentTurnIndex" to currentTurnIndex.coerceIn(0, 3),
        "currentTurnUid" to currentTurnUid,
        "diceValue" to diceValue.coerceIn(0, 6),
        "phase" to phase,
        "version" to 1,
        "boardStateJson" to boardStateJson.take(4000),
        "lastActionId" to lastActionId.take(128),
        "winnerUid" to winnerUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class MatchRecord(
    val matchId: String = "",
    val userId: String = "",
    val gameMode: String = "VS_AI",
    val opponentNames: String = "Royal AI",
    val winnerName: String = "RoyalPlayer",
    val isWin: Boolean = true,
    val durationSeconds: Int = 180,
    val coinsDelta: Int = 100,
    val xpEarned: Int = 75,
    val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "matchId" to matchId,
        "userId" to userId,
        "gameMode" to gameMode,
        "opponentNames" to opponentNames.take(200).ifBlank { "Opponent" },
        "winnerName" to winnerName.take(60).ifBlank { "Winner" },
        "isWin" to isWin,
        "durationSeconds" to durationSeconds.coerceIn(0, 86400),
        "coinsDelta" to coinsDelta.coerceIn(-10000, 100000),
        "xpEarned" to xpEarned.coerceIn(0, 100000),
        "createdAt" to FieldValue.serverTimestamp()
    )
}

data class AchievementDef(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val descriptionEn: String,
    val descriptionHi: String,
    val rewardCoins: Int,
    val rewardXp: Int,
    val targetValue: Int,
    val progressExtractor: (UserProfile, Int) -> Int
)

object AchievementCatalog {
    val all: List<AchievementDef> = listOf(
        AchievementDef(
            id = "first_victory",
            titleEn = "First Victory",
            titleHi = "पहली जीत",
            descriptionEn = "Win your first Ludo match",
            descriptionHi = "अपना पहला लूडो मैच जीतें",
            rewardCoins = 150,
            rewardXp = 100,
            targetValue = 1,
            progressExtractor = { profile, _ -> profile.wins }
        ),
        AchievementDef(
            id = "ten_wins",
            titleEn = "10 Wins",
            titleHi = "10 जीत",
            descriptionEn = "Claim 10 royal victories across any mode",
            descriptionHi = "किसी भी मोड में 10 शाही जीत हासिल करें",
            rewardCoins = 400,
            rewardXp = 250,
            targetValue = 10,
            progressExtractor = { profile, _ -> profile.wins }
        ),
        AchievementDef(
            id = "fifty_wins",
            titleEn = "50 Wins",
            titleHi = "50 जीत",
            descriptionEn = "Master the board with 50 victories",
            descriptionHi = "50 जीत के साथ बोर्ड पर महारत हासिल करें",
            rewardCoins = 1200,
            rewardXp = 800,
            targetValue = 50,
            progressExtractor = { profile, _ -> profile.wins }
        ),
        AchievementDef(
            id = "hundred_wins",
            titleEn = "100 Wins",
            titleHi = "100 जीत",
            descriptionEn = "Become an Emperor of Ludo with 100 wins",
            descriptionHi = "100 जीत के साथ लूडो सम्राट बनें",
            rewardCoins = 3000,
            rewardXp = 2000,
            targetValue = 100,
            progressExtractor = { profile, _ -> profile.wins }
        ),
        AchievementDef(
            id = "first_capture",
            titleEn = "First Capture",
            titleHi = "पहला कैप्चर",
            descriptionEn = "Capture an opponent token and send it back to base",
            descriptionHi = "विरोधी टोकन को काटकर बेस में भेजें",
            rewardCoins = 100,
            rewardXp = 75,
            targetValue = 1,
            progressExtractor = { profile, _ -> profile.capturesTotal }
        ),
        AchievementDef(
            id = "triple_capture",
            titleEn = "Triple Capture",
            titleHi = "ट्रिपल कैप्चर",
            descriptionEn = "Capture 3 or more opponent tokens in a single match",
            descriptionHi = "एक ही मैच में 3 या अधिक टोकन काटें",
            rewardCoins = 350,
            rewardXp = 200,
            targetValue = 3,
            progressExtractor = { _, matchCaptures -> matchCaptures }
        ),
        AchievementDef(
            id = "win_streak_3",
            titleEn = "Royal Win Streak",
            titleHi = "जीत की हैट्रिक",
            descriptionEn = "Win 3 matches in a row",
            descriptionHi = "लगातार 3 मैच जीतें",
            rewardCoins = 500,
            rewardXp = 300,
            targetValue = 3,
            progressExtractor = { profile, _ -> profile.bestStreak }
        ),
        AchievementDef(
            id = "perfect_finish",
            titleEn = "Veteran Tactician",
            titleHi = "अनुभवी रणनीतिकार",
            descriptionEn = "Reach Player Level 5",
            descriptionHi = "खिलाड़ी स्तर 5 तक पहुँचें",
            rewardCoins = 600,
            rewardXp = 400,
            targetValue = 5,
            progressExtractor = { profile, _ -> profile.level }
        )
    )
}
