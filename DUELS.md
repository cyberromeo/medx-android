# Duels (1v1) — design notes

Library ▸ **Duels** opens the hub: challenge a friend, quick match, join by code, create a room,
invites (incoming/sent), duels in progress (rejoin), recent opponents, history, and your duel stats
(rating, W/L/D, win rate, streak, peak).

## How a duel runs

1. **Deal.** The challenger's phone picks real QBank questions (one subject or Mixed; 5/10/20; 15–60 s
   each), writes them once to `medx_duels/{CODE}/deck/*`, and opens a lobby. Both players read the
   same deck, so the questions are identical.
2. **Meet.** Invite (accept takes the guest seat in one transaction), quick match (claim a waiting
   player in one transaction), or room code (join transaction). Inviting yourself, a second pending
   invite to the same person, joining a full/started/ended room, or starting a duel while one is live
   are all refused with a clear message.
3. **Ready → countdown.** Each phone marks itself ready when the duel screen opens. When both are
   ready, either phone writes `status: live, askedAt: now + 4 s` (compare-and-set), giving a synced
   3‑2‑1 countdown on Firestore server time (`ServerClock`, from commit times).
4. **Questions.** Each player writes only their own row (`answers.{q} = {o, c, ms, at}`), so
   simultaneous answers never collide. The opponent's row shows "Asha answered" live (progress
   dots too); their points only appear once a question is revealed.
5. **Advance.** When both answered (or the clock ran out) the answer is revealed for 2.6 s, then
   either phone writes the next `qIndex/askedAt` with a precondition on the game's `updateTime`;
   the slower write is rejected harmlessly. A silent player just times out — the match never stalls.
6. **Finish.** The last advance writes `status: done` and `result` (winner, reason, per-player
   scores, Elo delta for both, computed from ratings snapshotted at the start). Each player then
   applies the result to their own `medx_duel_stats/{uid}` once (game id remembered), and the duel
   is filed in the Activity log as a sitting.

**Scoring:** right answer = 100 + up to 100 for speed (linear in time left); wrong/timeout = 0.
Winner: most points → most correct → least time on correct answers → draw.
**Rating:** Elo, start 1200, K = 40 for the first 10 duels, then 24.

**Leaving / disconnects:** leaving the lobby closes it (and cancels the invite). Forfeit mid-match =
loss (reason `forfeit`). "Step away" keeps the duel running; a "Duel in progress · Rejoin" pill on
the tabs (and the hub's In progress list) reopens it — also after an app restart, since all state
is in Firestore. No heartbeat for 20 s shows "connection dropped"; after 60 s the other player can
**Claim the win** (reason `abandoned`). Offline shows a reconnecting banner; an answer that fails to
send is retried 3×.

## Firestore data model

| Path | Who writes | Fields |
|---|---|---|
| `publicProfiles/{uid}` | owner | uid, displayName, nameLower, searchKeys[] (word prefixes), photoUrl, emailHash (SHA‑256 of lower-cased email — no plain email), rating, lastActiveAt, inDuel, updatedAt |
| `medx_duel_stats/{uid}` | owner | uid, name, rating, played, wins, losses, draws, streak (+W/−L), bestStreak, peak, processed[] (last 40 game ids), lastPlayedAt |
| `medx_duel_invites/{inviteId}` | sender (create, cancel, expire) / recipient (accept, decline) | id, fromUid, fromName, fromPhoto, toUid, toName, toPhoto, status (pending/accepted/declined/cancelled/expired), gameId, settings{subjectId, subjectName, count, perQuestion}, createdAt, expiresAt (+10 min), participants[], rematchOf, respondedAt |
| `medx_duel_queue/{uid}` | owner; another player may only claim (waiting→matched) | uid, name, photoUrl, rating, status (waiting/matched), gameId (the waiting player's pre-dealt room), createdAt, heartbeatAt, matchedBy, matchedAt |
| `medx_duels/{CODE}` | host creates; guest takes seat; either player advances | **Faceoff fields kept**: uid (host), hostProfile, guestUid, guestProfile, status (lobby/live/done/abandoned), code, source{kind,id,name,subject}, total, deckParts, qIndex, askedAt, log[], createdAt, updatedAt, finishedAt. **New**: format:"duel", version:2, settings{…}, participants[], names{uid}, photos{uid}, ratings{uid}, played{uid}, origin (invite/quick/code), inviteId, createdAtMs, updatedAtMs, finishedAtMs, result{winner, reason, scores{uid:{points,correct,answered,correctMs}}, eloDelta{uid}} |
| `medx_duels/{CODE}/deck/{part}` | host, once | part, questions[] (full Question JSON, 10 per part — same shape Faceoff reads) |
| `medx_duel_players/{CODE}__{uid}` | that player only | uid, gameId, profile, left, joinedAt (Faceoff fields) + format, ready, heartbeatAt, answers{q:{o,c,ms,at,t}}, leftAt |

Queries used (all single-field, no composite index needed): invites by `toUid` / `fromUid`,
queue by `status == waiting`, profiles by `searchKeys array-contains` / `emailHash ==`,
games by `participants array-contains`.

### Compatibility with Faceoff, iOS and web
- Faceoff docs have no `format`; Duels ignore them (history/active use `participants`, which Faceoff
  never writes). Joining a Faceoff code from Duels opens the classic Faceoff room screen, and typing a
  Duels code into the classic Faceoff screen opens the Duels screen. Existing Faceoff docs are never
  modified.
- The iOS app (two hard-coded profiles, 60 s Faceoff) does not know `format: "duel"` and has no way
  to reach a Duels room; it is unaffected. The web app has no Faceoff/Duels code.

## Notifications and push
- In the foreground, an incoming invite shows as an in-app prompt over any screen (not during a
  live match); "Accept & play" takes the seat and opens the room. The inviter's phone polls every
  1.5 s while an invite is pending and jumps straight into the room when it's accepted.
- While the process is alive in the background, polling continues every 20 s and posts a local
  notification (channel "Duel invites"; Android 13+ asks for permission on first visit to Duels).
- **Gap:** when the app is killed (or Android freezes the cached process), nothing arrives. True push
  needs Firebase Cloud Messaging + a server (e.g. a Cloud Function on `medx_duel_invites` create).
  `DuelPush.onMessage(data)` / `onNewToken` are the hooks a FirebaseMessagingService would call.
- "Realtime" is adaptive REST polling (game + both player rows in one batchGet every 0.7 s during a
  match). The app has no Firestore SDK; snapshot listeners would cut latency further.

## Links
- Share text includes the room code and `medx://duel/CODE` (opens the app, joins the room).
- `medx://duel/invite/ID` (used by notifications) shows that invite's prompt.
- `https://medx.srihari.quest/duel/CODE` is parsed too, but there's no web page or App Link
  verification for it yet, so it isn't shared.

## Security
Rules are **not deployed**. The project currently lets any signed-in user read/write, which is why
this works today. Recommended rules: `firestore.rules.duels.suggested`. Even with them, a client can
write its own rating; tamper-proof ratings need a server-side function that applies `result`.
