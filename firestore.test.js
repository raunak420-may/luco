const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const nowTimestamp = () => new Date(Date.now() - 1000);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProfile(uid) {
  const ts = nowTimestamp();
  return {
    userId: uid,
    username: "RoyalPlayer",
    avatarIndex: 2,
    level: 1,
    xp: 0,
    coins: 1000,
    gamesPlayed: 0,
    wins: 0,
    losses: 0,
    currentStreak: 0,
    bestStreak: 0,
    capturesTotal: 0,
    unlockedAchievements: [],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validRoom(roomId, hostUid) {
  const ts = nowTimestamp();
  return {
    roomId: roomId,
    roomCode: "A7K9P2",
    hostUid: hostUid,
    hostName: "RoyalHost",
    mode: "PRIVATE_ROOM",
    status: "WAITING",
    maxPlayers: 4,
    playerUids: [hostUid],
    playerNames: ["RoyalHost"],
    readyUids: [hostUid],
    currentTurnIndex: 0,
    currentTurnUid: hostUid,
    diceValue: 0,
    phase: "WAITING_FOR_ROLL",
    version: 1,
    boardStateJson: "{}",
    lastActionId: "init_1",
    winnerUid: "",
    createdAt: ts,
    updatedAt: ts,
  };
}

test("1. Unauthenticated user cannot read or create profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
});

test("2. Authenticated user can create and read their own profile, but Bob cannot read Alice's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("3. Shadow update and negative coins value poisoning are rejected on profile update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));

  // Ghost field rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "QueenAlice",
      isAdmin: true,
      updatedAt: nowTimestamp(),
    })
  );

  // Value poisoning rejection (negative coins)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      coins: -100,
      updatedAt: nowTimestamp(),
    })
  );

  // Valid update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "QueenAlice",
      avatarIndex: 5,
      updatedAt: nowTimestamp(),
    })
  );
});

test("4. Leaderboard requires isPublic == true filter for list queries and blocks cross-user writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "QueenAlice",
      avatarIndex: 3,
      wins: 10,
      xp: 1200,
      level: 4,
      gamesPlayed: 15,
      winRatePercent: 66,
      isPublic: true,
      updatedAt: nowTimestamp(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Aligned query with isPublic == true succeeds
  await assertSucceeds(bobDb.collection("leaderboard").where("isPublic", "==", true).get());
  // Cross-user write fails
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      wins: 0,
      updatedAt: nowTimestamp(),
    })
  );
});

test("5. OnlineRoom enforces monotonic version increment, anti-replay lastActionId, and blocks terminal FINISHED updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const roomRef = aliceDb.collection("rooms").doc("room_101");
  await assertSucceeds(roomRef.set(validRoom("room_101", ALICE_UID)));

  // Bob joins room (version 1 -> 2)
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_101").update({
      playerUids: [ALICE_UID, BOB_UID],
      playerNames: ["RoyalHost", "KnightBob"],
      readyUids: [ALICE_UID, BOB_UID],
      status: "PLAYING",
      version: 2,
      lastActionId: "join_bob",
      updatedAt: nowTimestamp(),
    })
  );

  // Stale version (version 2 instead of 3) fails!
  await assertFails(
    aliceDb.collection("rooms").doc("room_101").update({
      diceValue: 6,
      phase: "WAITING_FOR_MOVE",
      version: 2,
      lastActionId: "roll_1",
      updatedAt: nowTimestamp(),
    })
  );

  // Valid turn update (version 2 -> 3) succeeds
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_101").update({
      diceValue: 6,
      phase: "WAITING_FOR_MOVE",
      version: 3,
      lastActionId: "roll_1",
      updatedAt: nowTimestamp(),
    })
  );

  // Finish game (version 3 -> 4)
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_101").update({
      status: "FINISHED",
      phase: "GAME_OVER",
      winnerUid: ALICE_UID,
      version: 4,
      lastActionId: "finish_1",
      updatedAt: nowTimestamp(),
    })
  );

  // Further updates after FINISHED are locked and rejected
  await assertFails(
    aliceDb.collection("rooms").doc("room_101").update({
      diceValue: 4,
      version: 5,
      lastActionId: "post_finish",
      updatedAt: nowTimestamp(),
    })
  );
});

test("6. Match history subcollection is owner-isolated and strictly append-only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const matchRef = aliceDb.collection("users").doc(ALICE_UID).collection("matches").doc("m_1");

  await assertSucceeds(
    matchRef.set({
      matchId: "m_1",
      userId: ALICE_UID,
      gameMode: "VS_AI",
      opponentNames: "Grandmaster AI",
      winnerName: "RoyalPlayer",
      isWin: true,
      durationSeconds: 420,
      coinsDelta: 150,
      xpEarned: 100,
      createdAt: nowTimestamp(),
    })
  );

  // Owner can query with userId filter
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("matches").where("userId", "==", ALICE_UID).get()
  );

  // Append-only: owner cannot update or delete match history
  await assertFails(matchRef.update({ coinsDelta: 99999 }));
  await assertFails(matchRef.delete());

  // Bob cannot read Alice's match history
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("matches").doc("m_1").get());
});
