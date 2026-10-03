# RunCoach

An Android app for run/walk training. After each run it reads your Garmin data, decides what
your next session should be, and builds a Spotify playlist from your own music at the tempo of
your running cadence.

```
Garmin watch → Garmin Connect → Health Connect → RunCoach → Spotify playlist
```

## What it does

1. **Finds your run.** Garmin Connect syncs workouts to Android Health Connect. RunCoach
   checks Health Connect every 30 minutes, and you can also tap **Check latest run**. It reads
   heart rate, speed, cadence, steps and distance.
2. **Analyses it against the plan.** It splits the run into the planned warm-up, run, walk and
   cool-down blocks (e.g. 7 × 3 min run / 1 min walk), then works out:
   - how much of the planned run time you actually ran (cadence ≥ 140 spm or speed ≥ 1.8 m/s counts as running)
   - average running HR as a % of max HR (measured max HR, or 208 − 0.7 × age)
   - fatigue drift: effort in the first third of reps vs the last third, as HR per unit speed
   - HR recovery during walk breaks
   - cadence, pace and stride length
   - optionally, how hard it felt (1–10), which you can add in the app afterwards
3. **Picks the next session.** It moves you up, keeps you on, or moves you down a ladder of
   sessions that runs from 8 × 1 min up to 45 min continuous:
   - **Step up** only if every signal says it was easy: ≥95 % completed, HR ≤85 % of max, drift ≤7 %, felt ≤6/10.
   - **Repeat** if one signal says it was hard.
   - **Step back** if two signals say hard, or you ran less than 80 % of the plan.
   - If your HR was too high it also suggests a slower pace.

   From 7 × 3/1 the next steps are 6 × 4/1, then 5 × 5/1, then 5 × 6/1, and so on
   (see `ProgressionEngine.LADDER`).
4. **Builds a playlist** from your Spotify top tracks, saved songs and recent plays. It looks up
   each song's tempo and keeps those within ±4 BPM of your cadence, counting half-time songs
   (an 85 BPM song fits 170 spm). Warm-up and cool-down get walking-tempo songs. If your
   cadence is under 165 spm, the run tempo is set ~3 % higher to nudge it up gently.

## Project layout

| Module | What's in it |
|---|---|
| `core/` | Pure Kotlin: analysis, progression ladder, cadence→BPM, playlist selection. Unit-tested, no Android needed. |
| `app/` | Android app (Jetpack Compose): Health Connect reader, Spotify login and API, tempo lookup, background worker, UI. |

## Setup

1. **Install the app.** Every push builds a new APK. On your Android phone, open
   <https://github.com/adamrowe70-ship-it/Test/releases/tag/runcoach-latest> and tap
   `app-debug.apk`. When Android asks, allow your browser to install unknown apps, then install.
   If Play Protect warns about an unrecognised app, choose *Install anyway* (it's your own build).
2. **Garmin → Health Connect.** In the Garmin Connect app on your phone: *More → Settings →
   Connected Apps → Health Connect*. Turn on sharing for activities, heart rate, steps and distance.
3. **Spotify developer app.** At <https://developer.spotify.com/dashboard>, create an app with
   the Web API and add the redirect URI `runcoach://callback`. Copy the Client ID.
   Spotify's Development Mode requires the app owner (you) to have Premium.
4. **In RunCoach:** under Settings, enter your age, your max HR if you know it, and the Spotify
   Client ID. Check the current session (it starts at 7 × 3 min / 1 min) and tap **Save**.
   Then tap **Connect** for Health Connect (allow everything, including background access) and
   for Spotify.
5. **Go running.** Once Garmin has synced, RunCoach notices the run within 30 minutes and
   notifies you with your next session and the playlist. You can also tap **Check latest run**.

To build it yourself instead, open this folder in Android Studio and run the `app` configuration.
Run the core tests without Android: `./gradlew :core:test`.

## Known limits

- **Data from Garmin.** It's unclear which detailed series Garmin writes to Health Connect
  (per-second HR, speed, cadence). The app uses whatever is there and falls back to step and
  distance totals. Check your first report: if pace or cadence is missing, tell me what
  Health Connect shows for the run.
- **Plan alignment.** The analysis assumes the recorded session starts with your warm-up walk.
  Start the watch activity when you start warming up, or set the warm-up to 0 in Settings.
- **Song tempo** comes from [ReccoBeats](https://reccobeats.com) (free, no key), because
  Spotify no longer gives song tempo to new apps. Songs it doesn't know are skipped. If the
  playlist is too short, raise the BPM tolerance in Settings.
- **Songs and intervals** can't line up exactly (songs are 3–4 min, intervals 1–3 min), so the
  run/walk block uses run-tempo music throughout.
- This is a training aid, not medical advice. If something hurts, stop.
