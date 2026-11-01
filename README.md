# MedX Elite for Android

A native Android port (Kotlin, Jetpack Compose, Material 3) of the iOS app **MedX Elite**. It runs
against the same Firebase project (`medx-e9acd`) as the iOS app and the web app at
<https://medx.srihari.quest>, so sittings, bookmarks, watch history, custom modules and the
syllabus checklist are shared across all three.

- Package: `quest.srihari.medxelite` · minSdk 26 · targetSdk/compileSdk 35
- Build: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- CI: `.github/workflows/android.yml` builds the debug APK and uploads it as the
  `medx-elite-debug-apk` artifact.

## Sign-in (the main change from iOS)

The iOS app had two hard-coded profiles behind a profile picker and a password prompt. This app has
an open sign-in screen for **any** account in the Firebase project:

| Option | How it works |
| --- | --- |
| **Continue with Google** | Android Credential Manager returns a Google ID token, which is exchanged for a Firebase session through Identity Toolkit `accounts:signInWithIdp`. Google-only accounts are checked against the web app's activation API (`/api/activation/status`), same gate as the website. |
| **Email + password** | Identity Toolkit `signInWithPassword` (as on iOS). Unverified emails are asked to verify first (same as the website), with a "resend verification" button. |
| **Forgot password** | `accounts:sendOobCode` with `PASSWORD_RESET`. |
| **Create account** | Opens <https://medx.srihari.quest/login> in a Custom Tab — accounts need an activation code, which only the website handles. |

The session (ID token + refresh token) persists across launches and refreshes itself through
`securetoken.googleapis.com`. **Settings ▸ Sign out** ends it. Everything iOS read from `Profile`
(id, name, initials, accent) now comes from the signed-in user (uid, display name or email, Google
photo). Data the iOS app filed under a profile id is filed under the uid.

## Firebase console steps

Email sign-in works out of the box. For Google sign-in:

1. **Add the Android app** — Firebase console ▸ `medx-e9acd` ▸ Project settings ▸ *Your apps* ▸
   **Add app ▸ Android**, package name `quest.srihari.medxelite`.
2. **Add the debug SHA-1** of the keystore this project ships (`keystore/debug.keystore`, used for
   every debug build, locally and in CI, so the fingerprint never changes):

   ```
   SHA-1:   A8:87:CF:51:BB:0F:F1:56:6D:26:9F:F2:1A:47:E3:EC:1A:FF:2A:7B
   SHA-256: 98:51:5B:FD:E3:4B:BC:16:3A:27:59:C0:5B:94:99:0C:EB:88:F5:CB:ED:E1:8E:F0:FC:63:0C:5E:63:E9:1A:56
   ```

   (Check with `keytool -list -v -keystore keystore/debug.keystore -storepass android`.)
   Add your release keystore's SHA-1 too when you make one.
3. **Enable the Google provider** — Authentication ▸ Sign-in method ▸ Google ▸ Enable (it already
   is for the website).
4. **Copy the Web client ID** — on that same Google provider page, *Web SDK configuration* ▸
   *Web client ID* (`300960747898-….apps.googleusercontent.com`).
5. Copy the **Android App ID** shown for the new app (`1:300960747898:android:…`) — optional.

### Constants to fill in

Either edit `app/src/main/java/quest/srihari/medxelite/Config.kt` (the two `TODO(Sri)` constants)
or, without touching code, add to `local.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=300960747898-xxxxxxxxxxxxxxxx.apps.googleusercontent.com
# optional — turns on the Firebase Auth SDK alongside the REST session
FIREBASE_ANDROID_APP_ID=1:300960747898:android:xxxxxxxxxxxxxxxx
```

In CI, set the same names as repository secrets. With `GOOGLE_WEB_CLIENT_ID` empty the Google button
explains what's missing and email sign-in still works. `FIREBASE_ANDROID_APP_ID` is optional: every
screen talks to Firestore/Identity Toolkit over REST with the API key, exactly like the iOS app; when
the ID is set, `FirebaseBridge` initialises the Firebase SDK from `FirebaseOptions` (no
`google-services.json` needed) and signs it in too, for future SDK-based features. Dropping in a
`google-services.json` with the plugin also works — the bridge reuses an existing default app.

## What's ported

| iOS | Android |
| --- | --- |
| Auth (profile picker + password) | Open sign-in: Google, email/password, forgot password, create account via web |
| Tab bar: Home, QBank, Tests, Classes, Library | Material 3 bottom navigation, same five tabs |
| Home: greeting, exam countdown (editable), daily goal ring, streak/correct/accuracy tiles, continue watching, revision due (1/3/7/21/45-day spacing), this-week bars, accuracy trend, syllabus checklist | Same |
| QBank: ARISE/Marrow banks, subjects → chapters → modules, practised counts, start sheet (Exam / Revision) | Same (`medx_qbank_subjects`, `medx_meta/qbank_fmge`, `medx_qbank_modules` + `_parts`) |
| Quiz runner: exam mode (one clock per block, sectioned grand tests with handover and no way back), revision mode (60 s per question, instant reveal), navigator grid, bookmarks, submit sheet, swipe between questions, timeout handling, save-and-leave / resume, haptics | Same |
| Sitting review: score, verdict, blocks, All/Wrong/Skipped filter, per-question options + explanation | Same |
| Attempts outbox + sync (`medx_attempts`, keyed by uid) | Same, offline-first |
| Tests: Marrow FMGE series (GTs / Mini / Subject, by month, best scores) | Same (`medx_meta/series_fmge`, `medx_test_questions`) |
| Batch papers (`medx_tests`) | Same |
| Flashcards: subjects, card grid, full-screen pager with pinch-zoom, phone/tablet + portrait/landscape artwork | Same |
| Classes: batches → subjects → videos, search, watch progress | Same |
| Video playback (AVPlayer + local HLS header-spoofing proxy) | Media3 ExoPlayer with the same per-host headers injected directly — no proxy; resume position; landscape toggle |
| VOD feed (`medx_vod`, newest first, paged) | Same |
| Bookmarks (`medx_bookmarks`), watch history (`medx_watch_history`), activity log | Same (with delete, practise-bookmarks, clear) |
| Settings: accent colour, appearance, daily goal, sync, cache, sign-out, diagnostics | Same |
| Syllabus tracker (`user_tracker/{uid}`) | Same |
| Custom modules (`medx_custom_modules`): build from both banks, shuffle, cap, run | Same — **now filtered to the signed-in user's own modules** (iOS showed the whole shared shelf because only two people used it) |
| Search | Subjects / chapters / modules across both banks + bookmarked question text |
| Faceoff duels | Ported rules (60 s, 40 + seconds left, first/winner, host closes rounds) over REST polling. **Changed:** any two users — the host creates a room whose id is a 6-character code, the other joins by typing it. |

## Skipped or reduced vs iOS

- **iOS-only system features:** widgets, Live Activities, App Intents/Siri, Spotlight indexing,
  Dynamic Island, iPad split view — not applicable.
- **Offline class downloads** (iOS `DownloadsView` + segment downloader) — not ported; classes stream.
- **VOD import into classes** (`VodImportView`, an admin action) — not ported.
- **Full-text question search** — iOS indexes every question body offline (`MedxQuestionIndexStore`);
  Android searches module/chapter/subject names and bookmarked questions only.
- **Demo mode** and debug autopilot — not ported.
- **Local avatar picker** — replaced by the Google profile photo / initials.
- **Faceoff "open lobbies" list and invite card on Home** — replaced by room codes.
- **HTML rendering** — questions render through Android's HTML parser; inline images are lifted out
  and shown below the text (tap to zoom). Complex tables are flattened to rows.

## Things to verify on a device

- **Faceoff depends on the deployed Firestore rules.** The repo's `firestore.rules` does not cover
  `medx_duels` / `medx_duel_players`. Room codes need: any signed-in user can *read*
  `medx_duels/{code}` (the iOS lobby list already implies this), the guest can create their own
  `medx_duel_players/{code}__{uid}`, and the host can *query* `medx_duel_players` where
  `gameId == code` to find who joined. If that query is refused the host never sees the guest.
- **Custom modules / bookmarks / attempts** rely on the existing rules (owner-only by `uid` / `ownerId`).
- Class streams depend on the CDNs accepting the same headers the iOS proxy sent.

## Project layout

```
app/src/main/java/quest/srihari/medxelite/
  Config.kt              backend constants + the two TODO constants
  Medx.kt                app singletons (auth, Firestore, stores)
  auth/                  REST auth, Credential Manager, optional Firebase SDK bridge
  data/                  models, Firestore REST client, disk cache, stores, catalogues
  ui/                    Compose screens (home, qbank, runner, tests, videos, flashcards,
                         library, custom, settings, faceoff) + theme + shared components
keystore/debug.keystore  shared debug signing key (password `android`) — debug only
```

## Building locally

Needs JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). Put `sdk.dir=…` in
`local.properties` (or set `ANDROID_HOME`), then `./gradlew assembleDebug`.
