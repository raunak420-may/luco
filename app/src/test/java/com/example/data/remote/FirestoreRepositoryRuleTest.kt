package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun profileCreateUpdateAndCrossUserIsolation_enforcesRules() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@ludo.test")
        val repo = FirestoreRepository(firestore, auth)

        val profileResult = withTimeout(5000L) { repo.getOrCreateProfile("LadyAlice") }
        assertTrue(profileResult.isSuccess)
        val profile = profileResult.getOrThrow()
        assertEquals(aliceUid, profile.userId)
        assertEquals("LadyAlice", profile.username)

        val updateResult = withTimeout(5000L) { repo.updateProfileCustomization("EmpressAlice", 4) }
        assertTrue(updateResult.isSuccess)

        // Cross-user read attempt by Bob should fail with PERMISSION_DENIED
        signInTestUser("bob_${UUID.randomUUID().toString().take(6)}@ludo.test")
        val bobRepo = FirestoreRepository(firestore, auth)
        val crossReadResult = withTimeout(5000L) { bobRepo.getUserProfileById(aliceUid) }
        assertTrue(crossReadResult.isFailure)
        val ex = crossReadResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun unauthenticatedCaller_rejectedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreRepository(firestore, auth)
        val unauthRead = withTimeout(5000L) { repo.getUserProfileById("some_user_123") }
        assertTrue(unauthRead.isFailure)
        val ex = unauthRead.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun onlineRoomCreationJoinAndAntiCheatTurnValidation_succeedsAndBlocksStaleVersion() = runBlocking {
        val aliceUid = signInTestUser("host_${UUID.randomUUID().toString().take(6)}@ludo.test")
        val aliceRepo = FirestoreRepository(firestore, auth)

        val roomRes = withTimeout(5000L) {
            aliceRepo.createOnlineRoom("HostAlice", "PRIVATE_ROOM", 2, "{\"tokens\":[]}")
        }
        assertTrue(roomRes.isSuccess)
        val room = roomRes.getOrThrow()
        assertNotNull(room.roomCode)
        assertEquals(1, room.version)

        // Bob joins and game starts (version 1 -> 2)
        val bobUid = signInTestUser("guest_${UUID.randomUUID().toString().take(6)}@ludo.test")
        val bobRepo = FirestoreRepository(firestore, auth)
        val joinRes = withTimeout(5000L) {
            bobRepo.joinOrReadyRoom(
                roomId = room.roomId,
                playerName = "GuestBob",
                markReady = true,
                startGameNow = true
            )
        }
        assertTrue(joinRes.isSuccess)
        assertEquals(2, joinRes.getOrThrow().version)
        assertEquals("PLAYING", joinRes.getOrThrow().status)

        // Bob (who is in room.playerUids) submits valid turn action (expectedVersion = 2 -> version 3)
        val turnRes = withTimeout(5000L) {
            bobRepo.submitValidatedTurnUpdate(
                roomId = room.roomId,
                expectedVersion = 2,
                actionId = "act_${UUID.randomUUID().toString().take(8)}",
                nextTurnIndex = 1,
                nextTurnUid = bobUid,
                diceValue = 6,
                phase = "WAITING_FOR_MOVE",
                boardStateJson = "{\"tokens\":[],\"rolled\":6}"
            )
        }
        assertTrue(turnRes.isSuccess)

        // Stale version (expectedVersion = 2 again) fails!
        val staleRes = withTimeout(5000L) {
            bobRepo.submitValidatedTurnUpdate(
                roomId = room.roomId,
                expectedVersion = 2,
                actionId = "act_stale_${UUID.randomUUID().toString().take(8)}",
                nextTurnIndex = 0,
                nextTurnUid = aliceUid,
                diceValue = 3,
                phase = "WAITING_FOR_ROLL",
                boardStateJson = "{}"
            )
        }
        assertTrue(staleRes.isFailure)
    }

    @Test
    fun recordMatchAndLeaderboardQueryAlignment_succeeds() = runBlocking {
        val uid = signInTestUser("champ_${UUID.randomUUID().toString().take(6)}@ludo.test")
        val repo = FirestoreRepository(firestore, auth)

        val matchRes = withTimeout(5000L) {
            repo.recordMatchAndRewards(
                gameMode = "VS_AI",
                opponentNames = "Royal AI (Hard)",
                winnerName = "RoyalChamp",
                isWin = true,
                durationSeconds = 300,
                capturesInMatch = 3
            )
        }
        assertTrue(matchRes.isSuccess)

        val leaderboard = withTimeout(5000L) {
            repo.observeLeaderboard().first { it.isNotEmpty() }
        }
        assertTrue(leaderboard.any { it.userId == uid })

        val history = withTimeout(5000L) {
            repo.observeMatchHistory(uid).first { it.isNotEmpty() }
        }
        assertTrue(history.any { it.userId == uid && it.isWin })
    }
}
