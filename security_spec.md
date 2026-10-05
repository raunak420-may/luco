# Security Specification (Phase 0 TDD) — Royal Dice Ludo

## 1. Data Invariants
1. **User Profile Ownership (`/users/{userId}`)**: A profile can only be read, created, or updated by the authenticated user whose `request.auth.uid == userId`. `userId` and `createdAt` are immutable.
2. **Leaderboard Isolation (`/leaderboard/{userId}`)**: Public leaderboard entries contain zero PII, require `isPublic == true` for list queries, and can only be written by `request.auth.uid == userId`.
3. **Multiplayer Room Integrity (`/rooms/{roomId}`)**:
   - Creation requires `hostUid == request.auth.uid`, `status == 'WAITING'`, and `version == 1`.
   - Updates require `isValidRoom(incoming())`, monotonic version increment (`incoming().version == existing().version + 1`), non-terminal existing state (`existing().status != 'FINISHED'`), and action-specific `affectedKeys().hasOnly(...)`.
4. **Match History Immutability (`/users/{userId}/matches/{matchId}`)**: Match history records can only be read and created by `request.auth.uid == userId`, and are strictly append-only (`allow update, delete: if false;`).

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Profile Read**: Unauthenticated client attempts `get /users/alice_123`.
2. **Cross-User Profile Read**: `bob_456` attempts `get /users/alice_123`.
3. **Identity Spoofing on Profile Create**: `alice_123` attempts to create `/users/alice_123` with `userId: "bob_456"`.
4. **Shadow Update (Ghost Field)**: `alice_123` updates `/users/alice_123` adding `isAdmin: true`.
5. **Value Poisoning on Coins**: `alice_123` updates `/users/alice_123` with `coins: -500` or a string `"infinite"`.
6. **Immortal Field Mutation**: `alice_123` updates `/users/alice_123` changing `createdAt`.
7. **Unfiltered List Query on Leaderboard**: Authenticated user queries `/leaderboard` without `where("isPublic", "==", true)`.
8. **Cross-User Leaderboard Tampering**: `bob_456` attempts to update `/leaderboard/alice_123`.
9. **Stale Version Replay Attack on Room**: Player submits an update to `/rooms/room_1` with `version == existing().version` instead of `existing().version + 1`.
10. **Terminal State Mutation on Room**: Player attempts to update `/rooms/room_1` after `status == 'FINISHED'`.
11. **Cross-User Match History Read**: `bob_456` attempts to list `/users/alice_123/matches`.
12. **Match History Tampering**: `alice_123` attempts to update or delete an existing `/users/alice_123/matches/m1` record.
