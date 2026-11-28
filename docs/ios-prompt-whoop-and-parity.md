# iOS prompt: WHOOP activity and Android parity

Paste everything below the line into a Claude Code session that runs in the
iOS project (branch `ios`, folder `ios/`).

---

Bring the iOS app (SwiftUI, branch `ios`) level with the Android app, which is on branch `android`. You can read the Android code with `git show origin/android:<path>`.

The server side is already built and deployed. Do not change it:

- the Supabase tables in `supabase/whoop.sql`;
- the Edge Function `whoop-callback`, with source in `supabase/functions/whoop-callback/index.ts` on branch `android`;
- the email templates.

Use only the publishable/anon Supabase key that the app already uses. Never use a service_role or secret key, and never use the WHOOP client secret.

Keep the app's current structure: SwiftUI views, view models and the store/service layers in `ios/WorkoutPlan`. Add unit tests for every piece of new logic. Commit and push only when I ask.

## 1. Name and orientation

- Show the app as **Slopevo** on the home screen. Set the display name with `INFOPLIST_KEY_CFBundleDisplayName`. Keep the bundle ID, so existing data carries over.
- Wherever the app names itself, say "Slopevo". Product references to the Workout Plan *website* stay as they are.
- Lock the iPhone to portrait. Set `INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone` to `UIInterfaceOrientationPortrait` for both Debug and Release.

## 2. Activity comes from WHOOP, not Apple Health

Activity is steps, sleep, recovery, strain, heart and workouts. It now comes from WHOOP through the `whoop-callback` function, exactly as on Android.

- Stop using Apple Health for Activity. Remove the Health permission screens from onboarding if nothing else needs them. Weight and food stay as they are.
- Remove the sample data from every real screen. Keep `SampleHealthStore`, or its replacement, for previews and tests only.

### The function's contract

`POST https://<SUPABASE_HOST>/functions/v1/whoop-callback`

Headers:
- `apikey: <publishable key>`
- `Authorization: Bearer <signed-in user's access token>`, refreshed first if it's about to expire, like the existing client does
- `Content-Type: application/json`

| Body | Reply |
|---|---|
| `{"action":"status"}` | `{"connected": true\|false}` |
| `{"action":"connect"}` | `{"url": "<WHOOP consent page>"}` |
| `{"action":"disconnect"}` | `{"connected": false}`; also revokes access at WHOOP |
| `{"action":"data","start":"<ISO-8601 instant>","end":"<ISO-8601 instant>"}` | `{"cycles":[…],"recoveries":[…],"sleeps":[…],"workouts":[…]}`: WHOOP's own v2 records. 409 means not connected; 400 means bad range (more than 400 days). |

Errors:
- 401 `{"error":"not_signed_in"}` when the token is missing or invalid.
- 500 `{"error":"server_error"}`.

**Connecting.** Get the `url` from `connect` and open it with `ASWebAuthenticationSession`, with `callbackURLScheme: "vitrack"`. After you approve, WHOOP redirects through the function to `vitrack://whoop-callback?result=connected|denied|expired|failed`.
- Read `result`, then call `status` to confirm.
- Show a short message for each result:
  - connected: "WHOOP connected"
  - denied: "WHOOP access wasn't allowed"
  - expired: "That took too long. Tap Connect to try again."
  - failed: "Couldn't connect WHOOP. Try again."
- `ASWebAuthenticationSession` catches the redirect itself, so you don't need to register a URL scheme in Info.plist.

The connection belongs to the Supabase account, not the device. A phone signed in to an account that's already connected sees `connected: true` straight away.

### Records the app reads

All other fields are ignored. Scores can be missing until WHOOP scores the record.

- **cycle:** `id` (int), `start`, `end?`, `timezone_offset` (like `"+08:00"`), `score?{strain, kilojoule, average_heart_rate, max_heart_rate}`, `step_count?` (int, null when no step data).
- **recovery:** `cycle_id`, `score?{recovery_score, resting_heart_rate, hrv_rmssd_milli}`.
- **sleep:** `cycle_id?`, `start`, `end?`, `nap` (bool), `score?{stage_summary{total_in_bed_time_milli, total_awake_time_milli, total_light_sleep_time_milli, total_slow_wave_sleep_time_milli, total_rem_sleep_time_milli}, sleep_performance_percentage?}`.
- **workout:** `start`, `end`, `timezone_offset`, `sport_name`, `score?{strain, average_heart_rate, max_heart_rate, zone_durations{zone_one_milli…zone_five_milli}}`.

### Turning records into days

Port these rules from `app/src/main/java/com/vitrack/app/data/whoop/WhoopMapper.kt` on branch `android`, and port its tests (`app/src/test/java/com/vitrack/app/data/whoop/WhoopTest.kt`) with the same sample JSON.

- **A cycle's day:** take `start` in its `timezone_offset`, add 12 hours, and use that date. Falling asleep at 23:30 on the 27th counts as the 28th.
- **Each day uses its own cycle:**
  - strain;
  - steps = `step_count`, or 0;
  - calories = kilojoule ÷ 4.184, rounded;
  - average and max heart rate;
  - recovery = that cycle's `recovery_score`, rounded;
  - sleep performance and sleep minutes come from that cycle's main sleep, where `nap == false`. Sleep minutes = (light + deep + REM) ÷ 60,000, which excludes time awake.
- **Days without a cycle** are all zeros. A history always has one entry per day.
- **Heart:**
  - resting heart rate and HRV come from the shown day's recovery;
  - the 7-day resting line repeats the previous value on a missing day.
- **Sleep stages:** awake, light, deep (slow wave) and REM, each as a share of their total.
- **Workouts:** those whose `start`, in their offset, falls on the shown day, sorted by start.
  - Zones: zones 1–5 as shares of their total; zone 0 is excluded.
  - Kind: Cycling if the name contains "cycl", "bik" or "spin"; Walking for "walk" or "hik"; otherwise Other.
  - Name: `sport_name` with `_` and `-` turned into spaces and the first letter capitalised.
- **Fetching:**
  - Ask from the start of the day before the range's first day, so the first day's evening-start cycle is included, up to now.
  - Keep records for 5 minutes, so the tabs don't each fetch.
  - Make one request at a time: a refresh invalidates the previous token.
  - On a network or server error, try again after 30 s. On 409, switch to not connected.

## 3. Not connected and not signed in

- With WHOOP not connected, Activity and its detail screens show a **Connect WHOOP** screen instead of numbers:
  - an icon;
  - the title "Connect WHOOP";
  - "Your steps, strain, recovery, sleep and workouts come from WHOOP. Connect it to see them here.";
  - a **Connect WHOOP** button that starts the flow above, with any error shown under it.
- WHOOP needs an account. If you're signed out, the button opens the existing Sign in first, then continues to connect.
- **Review** leaves out the activity section when there is no activity.
- **Trainer links** include no activity section when there is no activity. Never send sample numbers to a trainer.
- **Account:**
  - add a WHOOP row showing "Connected · your WHOOP data" or "Not connected";
  - show a **Connect** or **Disconnect** button. Disconnect asks first: "Slopevo stops reading your WHOOP data and deletes its access. Activity stays empty until you connect again.";
  - call `status` when Account opens.

## 4. Day picker on Activity

- Add a calendar button in the Activity header. It opens a date picker covering the last 365 days up to today. Future days can't be picked, and it opens on the day being shown.
- Picking a day shows that day: the date heading, steps, the sleep, recovery and strain rings, the Sleep and Heart cards, and the workouts.
  - While a day loads, show a loading state, never the previous day's numbers under the new date.
  - Picking today is the same as going back to today.
- On a past day:
  - the date heading turns accent-coloured, with a **Back to today** button next to it;
  - the "From WHOOP · synced …" line is shown only for today.
- The weekly steps chart and its range picker still count back from today.

## 5. Sign-in by emailed code

The sign-in email now leads with a large code, and its link is only a fallback for the same device. iOS already signs in with a code, so keep that flow and only change the wording and errors:

- The email step says "We'll email you a sign-in code."
- The code step says "We sent a code to <email>. Enter it below. You can open this email anywhere, such as on your computer."
- Map server errors to plain words:
  - HTTP 429, or a message containing "rate limit": "Too many tries in a short time. Wait a minute, then try again."
  - HTTP 403, or "expired or is invalid": "That code is wrong or has expired. Check the newest email, or ask for a new code."
  - Accept codes of 6 to 10 digits.

## Done means

- The app builds, and all unit tests pass.
- New tests cover:
  - the mapping rules;
  - the client's requests and its 409 handling;
  - the result link parsing;
  - Activity showing Connect WHOOP when not connected;
  - picking a past day and going back to today;
  - the error mapping.
- Checked in the simulator:
  - the Connect WHOOP screen, signed in but not connected;
  - the day picker and Back to today, on an account already connected on Android, which should show as connected without reconnecting;
  - the app name and the portrait lock.
- Tell me anything you couldn't verify. For example, real WHOOP numbers may be zero until the WHOOP membership is active again on 1 December 2026.
