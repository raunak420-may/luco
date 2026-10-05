package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.model.AchievementCatalog
import com.example.model.LeaderboardEntry
import com.example.model.MatchRecord
import com.example.model.OnlineRoom
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    // Secondary constructor resolving the custom database ID from resources
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // 1. User Profile Operations
    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun getOrCreateProfile(defaultName: String = "RoyalMonarch"): Result<UserProfile> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid, username = defaultName)
                Result.success(existing)
            } else {
                val safeName = defaultName.trim().take(30).let { if (it.length < 2) "Monarch" else it }
                val newProfile = UserProfile(
                    userId = uid,
                    username = safeName,
                    avatarIndex = (uid.hashCode().and(0x7FFFFFFF)) % 8,
                    level = 1,
                    xp = 0,
                    coins = 1000
                )
                docRef.set(newProfile.toCreateMap()).await()
                syncLeaderboardEntry(newProfile)
                Result.success(newProfile)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfileById(targetUid: String): Result<UserProfile?> {
        val path = "users/$targetUid"
        return try {
            val snap = db.collection("users").document(targetUid).get().await()
            val profile = if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(profile)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfileCustomization(username: String, avatarIndex: Int): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        val cleanName = username.trim().take(30).let { if (it.length < 2) "Player" else it }
        val cleanAvatar = avatarIndex.coerceIn(0, 11)
        return try {
            val updates = mapOf(
                "username" to cleanName,
                "avatarIndex" to cleanAvatar,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(updates).await()
            val updatedSnap = db.collection("users").document(uid).get().await()
            val profile = updatedSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (profile != null) {
                syncLeaderboardEntry(profile)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordMatchAndRewards(
        gameMode: String,
        opponentNames: String,
        winnerName: String,
        isWin: Boolean,
        durationSeconds: Int,
        capturesInMatch: Int
    ): Result<MatchRecord> {
        val uid = requireUserId()
        val userPath = "users/$uid"
        return try {
            val profileResult = getOrCreateProfile()
            val current = profileResult.getOrThrow()

            val baseCoins = if (isWin) 200 else 40
            val baseXp = if (isWin) 120 else 35
            val newGamesPlayed = current.gamesPlayed + 1
            val newWins = if (isWin) current.wins + 1 else current.wins
            val newLosses = if (!isWin) current.losses + 1 else current.losses
            val newStreak = if (isWin) current.currentStreak + 1 else 0
            val newBestStreak = maxOf(current.bestStreak, newStreak)
            val newCaptures = current.capturesTotal + capturesInMatch

            val tempProfile = current.copy(
                gamesPlayed = newGamesPlayed,
                wins = newWins,
                losses = newLosses,
                currentStreak = newStreak,
                bestStreak = newBestStreak,
                capturesTotal = newCaptures
            )

            // Evaluate newly unlocked achievements
            val unlockedSet = current.unlockedAchievements.toMutableSet()
            var bonusCoins = 0
            var bonusXp = 0
            for (ach in AchievementCatalog.all) {
                if (!unlockedSet.contains(ach.id)) {
                    val progress = ach.progressExtractor(tempProfile, capturesInMatch)
                    if (progress >= ach.targetValue && unlockedSet.size < 30) {
                        unlockedSet.add(ach.id)
                        bonusCoins += ach.rewardCoins
                        bonusXp += ach.rewardXp
                    }
                }
            }

            val totalCoinsDelta = baseCoins + bonusCoins
            val totalXpEarned = baseXp + bonusXp
            val finalCoins = (current.coins + totalCoinsDelta).coerceIn(0, 100000000)
            val finalXp = (current.xp + totalXpEarned).coerceIn(0, 100000000)
            val finalLevel = ((finalXp / 250) + 1).coerceIn(1, 1000)

            val profileUpdates = mapOf(
                "username" to current.username,
                "avatarIndex" to current.avatarIndex,
                "level" to finalLevel,
                "xp" to finalXp,
                "coins" to finalCoins,
                "gamesPlayed" to newGamesPlayed,
                "wins" to newWins,
                "losses" to newLosses,
                "currentStreak" to newStreak,
                "bestStreak" to newBestStreak,
                "capturesTotal" to newCaptures,
                "unlockedAchievements" to unlockedSet.toList().take(30),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(profileUpdates).await()

            val updatedProfile = tempProfile.copy(
                level = finalLevel,
                xp = finalXp,
                coins = finalCoins,
                unlockedAchievements = unlockedSet.toList().take(30)
            )
            syncLeaderboardEntry(updatedProfile)

            val matchId = "m_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val matchRecord = MatchRecord(
                matchId = matchId,
                userId = uid,
                gameMode = gameMode,
                opponentNames = opponentNames.ifBlank { "Royal Opponent" },
                winnerName = winnerName.ifBlank { "Royal Player" },
                isWin = isWin,
                durationSeconds = durationSeconds.coerceIn(0, 86400),
                coinsDelta = totalCoinsDelta,
                xpEarned = totalXpEarned
            )
            val matchRef = db.collection("users").document(uid).collection("matches").document(matchId)
            matchRef.set(matchRecord.toCreateMap()).await()
            Result.success(matchRecord)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userPath)
            Result.failure(e)
        }
    }

    // 2. Leaderboard Operations (Aligned with existing().isPublic == true)
    suspend fun syncLeaderboardEntry(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val path = "leaderboard/$uid"
        return try {
            val entry = LeaderboardEntry(
                userId = uid,
                username = profile.username,
                avatarIndex = profile.avatarIndex,
                wins = profile.wins,
                xp = profile.xp,
                level = profile.level,
                gamesPlayed = profile.gamesPlayed,
                winRatePercent = profile.winRatePercent,
                isPublic = true
            )
            val docRef = db.collection("leaderboard").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updateMap = mapOf(
                    "username" to entry.username.take(30).padEnd(2, 'X'),
                    "avatarIndex" to entry.avatarIndex.coerceIn(0, 11),
                    "wins" to entry.wins.coerceAtLeast(0),
                    "xp" to entry.xp.coerceAtLeast(0),
                    "level" to entry.level.coerceIn(1, 1000),
                    "gamesPlayed" to entry.gamesPlayed.coerceAtLeast(0),
                    "winRatePercent" to entry.winRatePercent.coerceIn(0, 100),
                    "isPublic" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            } else {
                docRef.set(entry.toWriteMap()).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeLeaderboard(): Flow<List<LeaderboardEntry>> {
        val path = "leaderboard"
        return db.collection("leaderboard")
            .whereEqualTo("isPublic", true)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    LeaderboardEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.wins * 10000 + it.xp }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    // 3. Match History Operations (Scoped to users/{userId}/matches with userId filter)
    fun observeMatchHistory(userId: String): Flow<List<MatchRecord>> {
        val path = "users/$userId/matches"
        return db.collection("users").document(userId).collection("matches")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    MatchRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    // 4. Online Rooms & Private Rooms Operations
    suspend fun createOnlineRoom(
        hostName: String,
        mode: String,
        maxPlayers: Int,
        initialBoardJson: String
    ): Result<OnlineRoom> {
        val uid = requireUserId()
        val roomId = "room_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val roomCode = generateUniqueRoomCode()
        val path = "rooms/$roomId"
        return try {
            val cleanHostName = hostName.trim().take(30).let { if (it.length < 2) "Host" else it }
            val room = OnlineRoom(
                roomId = roomId,
                roomCode = roomCode,
                hostUid = uid,
                hostName = cleanHostName,
                mode = mode,
                status = "WAITING",
                maxPlayers = maxPlayers.coerceIn(2, 4),
                playerUids = listOf(uid),
                playerNames = listOf(cleanHostName),
                readyUids = listOf(uid),
                currentTurnIndex = 0,
                currentTurnUid = uid,
                diceValue = 0,
                phase = "WAITING_FOR_ROLL",
                version = 1,
                boardStateJson = initialBoardJson.ifBlank { "{}" },
                lastActionId = "create_${UUID.randomUUID().toString().take(8)}",
                winnerUid = ""
            )
            db.collection("rooms").document(roomId).set(room.toCreateMap()).await()
            Result.success(room)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun findWaitingRoomByCode(roomCode: String): Result<OnlineRoom?> {
        val path = "rooms"
        val normalized = roomCode.trim().uppercase()
        return try {
            val snap = db.collection("rooms")
                .whereEqualTo("status", "WAITING")
                .whereEqualTo("roomCode", normalized)
                .limit(1)
                .get()
                .await()
            val room = snap.documents.firstOrNull()?.toObject(
                OnlineRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(room)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun findOpenQuickMatchRoom(): Result<OnlineRoom?> {
        val path = "rooms"
        return try {
            val snap = db.collection("rooms")
                .whereEqualTo("status", "WAITING")
                .whereEqualTo("mode", "QUICK_MATCH")
                .limit(10)
                .get()
                .await()
            val rooms = snap.toObjects(
                OnlineRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val available = rooms.firstOrNull { it.playerUids.size < it.maxPlayers }
            Result.success(available)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun findReconnectableRoomForUser(userId: String): Result<OnlineRoom?> {
        val path = "rooms"
        return try {
            val snap = db.collection("rooms")
                .whereArrayContains("playerUids", userId)
                .limit(10)
                .get()
                .await()
            val rooms = snap.toObjects(
                OnlineRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val active = rooms.firstOrNull { it.status == "PLAYING" || it.status == "WAITING" }
            Result.success(active)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun joinOrReadyRoom(
        roomId: String,
        playerName: String,
        markReady: Boolean = true,
        startGameNow: Boolean = false,
        updatedBoardJson: String? = null
    ): Result<OnlineRoom> {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        return try {
            val docRef = db.collection("rooms").document(roomId)
            val snap = docRef.get().await()
            val current = snap.toObject(
                OnlineRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Room not found"))

            if (current.status != "WAITING") {
                return Result.failure(IllegalStateException("Room is no longer waiting for players"))
            }
            if (!current.playerUids.contains(uid) && current.playerUids.size >= current.maxPlayers) {
                return Result.failure(IllegalStateException("Room is already full"))
            }

            val newUids = current.playerUids.toMutableList()
            val newNames = current.playerNames.toMutableList()
            val cleanName = playerName.trim().take(30).let { if (it.length < 2) "Player" else it }
            if (!newUids.contains(uid)) {
                newUids.add(uid)
                newNames.add(cleanName)
            }
            val newReady = current.readyUids.toMutableSet()
            if (markReady) newReady.add(uid) else newReady.remove(uid)

            val newStatus = if (startGameNow && newUids.size >= 2) "PLAYING" else "WAITING"
            val nextVersion = current.version + 1
            val actionId = "lobby_${UUID.randomUUID().toString().take(12)}"
            val boardJson = updatedBoardJson ?: current.boardStateJson

            val updates = mapOf(
                "playerUids" to newUids,
                "playerNames" to newNames,
                "readyUids" to newReady.toList(),
                "status" to newStatus,
                "currentTurnIndex" to 0,
                "currentTurnUid" to newUids.first(),
                "boardStateJson" to boardJson,
                "version" to nextVersion,
                "lastActionId" to actionId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(
                current.copy(
                    playerUids = newUids,
                    playerNames = newNames,
                    readyUids = newReady.toList(),
                    status = newStatus,
                    boardStateJson = boardJson,
                    version = nextVersion,
                    lastActionId = actionId
                )
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitValidatedTurnUpdate(
        roomId: String,
        expectedVersion: Int,
        actionId: String,
        nextTurnIndex: Int,
        nextTurnUid: String,
        diceValue: Int,
        phase: String,
        boardStateJson: String,
        status: String = "PLAYING",
        winnerUid: String = ""
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        return try {
            val docRef = db.collection("rooms").document(roomId)
            val snap = docRef.get().await()
            val current = snap.toObject(
                OnlineRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Room not found"))

            if (current.status != "PLAYING") {
                return Result.failure(IllegalStateException("Game is not in PLAYING state"))
            }
            if (current.version != expectedVersion) {
                return Result.failure(IllegalStateException("Stale game version: expected $expectedVersion, found ${current.version}"))
            }
            if (current.lastActionId == actionId) {
                return Result.failure(IllegalStateException("Duplicate action rejected: $actionId"))
            }
            if (!current.playerUids.contains(uid)) {
                return Result.failure(IllegalStateException("Unauthorized player for this room"))
            }

            val updates = mapOf(
                "currentTurnIndex" to nextTurnIndex.coerceIn(0, 3),
                "currentTurnUid" to nextTurnUid,
                "diceValue" to diceValue.coerceIn(0, 6),
                "phase" to phase,
                "version" to (expectedVersion + 1),
                "boardStateJson" to boardStateJson.take(4000),
                "lastActionId" to actionId.take(128),
                "status" to status,
                "winnerUid" to winnerUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeOnlineRoom(roomId: String): Flow<OnlineRoom?> {
        val path = "rooms/$roomId"
        return db.collection("rooms").document(roomId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        OnlineRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else null
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    private fun generateUniqueRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}
