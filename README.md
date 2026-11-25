# Training Plan

A mobile first Next.js app for the restart plan (重新開始計劃 tab) of the "Weekly Training Plan (Trainer's 3 Day Rotation)" Google Sheet: A Hinge Day → B Squat Day → C Back Day, straight sets in order, restart weights about 25% below the last trainer session (June 2025). Form cues, warm ups, substitutes and videos come from the sheet's Training Plan tab. The plan lives in [`src/lib/plan.ts`](src/lib/plan.ts).

## Features

* **Three tabs, one job each**: **Today** (which day it is and what to lift), **Progress** (are you getting stronger) and **Body** (weight, photos, nutrition). Account is the icon in Today's header. The design lives on a Claude Design canvas.
* **Today**: the next day in your rotation, every exercise with today's weight and what changed (↑ add, Hold, ↓ back off, First time), then **Start workout**. You can start any other day instead, and **Edit plan** is one tap away.
* **Workout**: a set table per exercise — Set · Previous · kg · Reps · ✓. Tap a cell to type; ✓ turns the row green and fills blank cells with the suggested weight and target reps. A **W** row shows the sheet's warm-up sets worked out from today's weight. Notes sit under each exercise name; form cues, tutorials and the equipment-taken substitute are one tap away. Duration, volume and sets stay pinned at the top, and the tab bar hides while you log. There is no rest timer.
* **Save workout**: Finish opens a save screen with the totals, a **photo**, how it went, and the weights your next session of that day will open with.
* **Progression** follows the sheet: hit the target reps on every set → +1.25 kg next time (2.5 lb); any set short → hold; under 8 reps after an increase → back to the previous weight. Bodyweight moves build reps.
* **Progress**: per day, one line per exercise with a trend line, the current weight and the next-session verdict with its reason. Tap an exercise for its chart (heaviest set or volume, with your June 2025 trainer level as a reference line) and every session.
* **Edit your plan**: Today → Edit plan. Reorder days (letters follow the order), set your training weekdays, and add a new day (e.g. D Arm Day) as part of the rotation or as an optional extra. In a day, add exercises from the built-in library or create your own, set sets, reps, start weight, warm-up and a substitute, reorder or remove them. A, B and C open in the same editor. Until you first save, the built-in restart plan is used.
* **Restart weights**: the built-in A/B/C come from the sheet's 重新開始計劃 tab, with restart weights about 25% below the last trainer session.
* **Photos**: add photos on the Save screen and to any body weight entry (camera icon in Body history). Photos are resized to JPEG (max 1600 px, location data dropped) and stored privately in your account. Discarding a workout or deleting a weight entry deletes its files, and account deletion removes all of them.
* **Keep screen on** toggle at the end of the workout, where the browser supports it.
* **Body weight**: latest weight and trend first, then **Log weigh-in**, then history with progress photos; download as CSV.
* **Nutrition calculator**: Body → **Nutrition targets** shows separate **Workout day** and **Non-workout day** calories, protein, carbs and fat from body weight (kg or lb), activity outside workouts and weight goal. Non-workout maintenance uses 28/33/38 kcal/kg for light/moderate/high daily activity; workout maintenance adds a rough 4 kcal/kg session allowance before applying the goal (−15% loss, unchanged maintenance, +10% gain). Protein and fat stay the same on both days, with extra workout calories allocated to carbs. This is not measured exercise expenditure or automatic workout-log detection. It automatically reads the latest entry from the Weight module and shows its logged date and preferred unit, with an **Update weight** link. There is no duplicate weight input or saving in Nutrition; changing or deleting a Weight entry recalculates targets automatically. Without a logged weight, a single **Log weight** link gets you started. Activity and goal choices are temporary. These adult-only heuristics are explained in the module and are not personalized medical advice. Targets below 1,500 kcal/day are withheld separately for each day, not clamped to a universal minimum. No database migration is needed for this calculator.
* **kg or lb**: every exercise starts in kg; tap the kg column header in the workout to switch, and it's remembered for that exercise.
* Installable to the home screen, in a single dark theme.

Sign-in is required before accessing any application page. Use the email code to sign in; an existing session is restored automatically. **Supabase is the source of truth:** workouts, in-progress drafts (sets, reps, completion, substitutes and notes), body-weight records, unit choices, the rest timer and the keep-screen-on preference are stored in the database. There is no local workout cache, offline mode, backup feature or manual sync control. Only the authentication session is persisted in browser storage.

The app loads the signed-in account's data before showing training pages. Every edit immediately starts an authenticated database write; rapid edits are serialized in memory to preserve their order. Saving indicators and failures are visible, and Save/Finish success is shown only after database confirmation. If a write fails, keep the page open and use **Retry saving** (reconnecting also retries). Unsaved edits are held only in memory and are lost if the page is closed; the browser warns before leaving while writes are outstanding. Sign-out waits for writes and refuses to proceed if they fail.

Reloading or signing in on another device loads the latest database records. Simultaneous edits to the same draft are last-write-wins; this is not a live collaborative editor. Temporary UI state such as open dialogs, chart filters and unsubmitted form fields is not account data. Static plan and guidance content remain in the source code. If Supabase is not configured or account data cannot load, access stays blocked.

The sign-in screen is a client-side application gate, not server-side authorization for static plan content. Supabase row-level security protects each account's stored data.

### Delete an account

In **Account → Delete account…**, review the warning, type `DELETE`, then choose **Delete permanently**. This removes the signed-in Supabase Auth user and their workout history, drafts, body weights, custom plans, added exercises, preferences and mutation receipts in one database transaction, then signs out this browser. Other accounts are untouched. Deletion cannot be undone; signing up again creates an empty account. Other devices cannot refresh the deleted user's session; already-issued access tokens can remain valid until expiry, but the deleted user's records are gone and foreign keys prevent new records for that user. Provider-managed backups follow the provider's retention policy.

**Deployment:** rerun the updated [schema](<supabase/schema.sql>) in the Supabase SQL Editor before deploying this feature. It installs the authenticated-only `delete_own_account` function (`SECURITY DEFINER`, empty search path), validates the caller's own user ID and exact confirmation, and never accepts a target account to delete. No service-role key or additional environment variable is required. Without this migration, deletion shows an explicit setup error instead of claiming success.

## Supabase setup (required database and authentication)

**Existing installations must rerun the updated [schema](<supabase/schema.sql>) before deploying.** The latest version adds the account's editable plan (`settings.plan`), the `workout_sessions` table for the Save screen, photo columns on workout history and body weights, and the private `photos` storage bucket with a policy that limits each account to its own folder. Without it, uploading a photo shows a setup error. It preserves existing log, weight and settings rows, adds workout drafts, custom training plans and timer/preferences columns, and installs the authenticated transactional write function. Existing database records remain available. Legacy phone-only records are not automatically imported or deleted; export any needed local records from the old version before upgrading.

1. Create a free project at [supabase.com](https://supabase.com).
2. **SQL Editor** → New query → paste [`supabase/schema.sql`](supabase/schema.sql) → **Run**. This creates the tables and the security rules that keep each account's data private.
3. **Authentication** → **Emails**: in both the **Magic Link** and **Confirm signup** templates, add this line so the email includes a code:
   ```html
   <p>Your sign in code: <strong>{{ .Token }}</strong></p>
   ```
   (A home screen app can't receive a link from the email, so you type the code instead.)
4. **Authentication** → **URL Configuration**: set **Site URL** to your Vercel address, e.g. `https://workout-plan-ruddy.vercel.app`.
5. **Project Settings** → **API Keys**, then add these in Vercel (Project → Settings → Environment Variables) and redeploy:

   | Name | Value |
   |---|---|
   | `NEXT_PUBLIC_SUPABASE_URL` | Project URL, e.g. `https://abcd1234.supabase.co` |
   | `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | the `sb_publishable_...` key (or the legacy **anon public** key) |

   Never use the secret or service_role key here.

   For local development, copy [`.env.example`](.env.example) to `.env.local` and fill in the same two values.

Supabase's built in email only sends a few emails an hour, to your own team's addresses, which is fine for a personal app. For other users, configure a custom SMTP provider in Supabase. Without the two environment variables, the app displays a configuration message and blocks access.

## Exercise library and custom exercises

Each built-in day has an **Add exercise** button. Search the bundled **Free Exercise DB** catalog (876 exercises) by name, equipment or muscle, choose working sets/reps/rest, and add straight sets to that day. Searching is local and makes no external API requests; no exercise-service API key or subscription is needed. Existing supersets and authored YouTube tutorials are unchanged.

If the exercise is missing, choose **Add custom exercise**. Enter a name plus optional equipment, muscles and instructions, then set your working targets. The library and custom definitions, names, instructions and prescriptions are saved as account-owned snapshots in `settings.day_exercises`. They load on other devices, support drafts/logging/unit preferences, and survive completing or discarding a workout. Removing one clears only its in-progress sets, not completed logs. A day supports up to 30 added exercises. Custom exercises remain private to the account/day; they do not enter a public shared catalog.

### Upgrade and setup

1. Rerun the updated [schema](<supabase/schema.sql>) **before deploying**. It updates the transactional validator to accept library/custom snapshots while preserving old records.
2. Deploy the updated app. Only the existing Supabase configuration is required. Remove the unused `MUSCLEWIKI_API_KEY` from deployment secrets if present.
3. Existing MuscleWiki references and history are preserved, but no provider lookup runs. Old entries show their reference ID and can still be logged/removed; replace them from the new library or custom form to get permanent names/instructions.

### Dataset and licensing

Text metadata is vendored from [Free Exercise DB](https://github.com/yuhonas/free-exercise-db), revision `f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5`, under its [Unlicense](<src/data/FREE-EXERCISE-DB-LICENSE.txt>). Source revision, original data SHA-256 and record count are recorded in [source metadata](<src/data/exercise-library-source.json>). No upstream images, videos or remote media URLs are imported; selected text is retained in the database. General instructions are not personalized medical advice.

To refresh the pinned text catalog intentionally, review/update the revision in [the import script](<scripts/import-exercise-library.mjs>) and run `node scripts/import-exercise-library.mjs`. Review dataset/license changes and run tests before deploying. Normal builds and searches do not download anything. The in-app [data notice](<src/app/data-notice/page.tsx>) describes attribution and data handling.

## Updating the plan

The plan lives in [the plan module](<src/lib/plan.ts>). Edit it when the sheet changes.

## Run locally

```bash
npm install
npm run dev
```

## Validation

```bash
npm test
npm run typecheck
npm run build
```

The tests execute the schema in embedded PostgreSQL, exercise RLS isolation and atomic/idempotent writes, and test store ordering, failure retries and account/sign-out races. They do not connect to a deployed Supabase project.

## Deploy

Push to GitHub and import the repo on [Vercel](https://vercel.com/new). Configure the Supabase environment variables above before deploying; sign-in is required.

On iPhone, open the site in Safari, tap Share, then tap **Add to Home Screen**. On Android, open it in Chrome, open the menu, then tap **Add to Home screen**.

## Muse AI connector (MCP)

`/api/muse/mcp` is an MCP server (Streamable HTTP) for Muse AI's custom
connector. Tools: `get_body_weight` (weekly averages and whether weight is
dropping), `log_body_weight`, `delete_body_weight`, `get_plan`,
`get_workouts`, `get_exercise_history`, `log_workout` and `delete_workout`.

It authenticates Muse with a Bearer token and acts as one account, signing in
with that account's email and password, so row-level security still applies.
Set these in Vercel, then redeploy:

| Variable | Value |
| --- | --- |
| `WORKOUT_EMAIL` | The account's email |
| `WORKOUT_PASSWORD` | The account's password (set on the Account page) |
| `WORKOUT_TIMEZONE` | Optional, for "today". Defaults to `Asia/Singapore` |
| `MUSE_MCP_TOKEN` | Optional. A fixed token of 32+ characters, if you'd rather not generate one in the app |

Then, signed in as that account, open **Account → Muse AI connector** and tap
**Generate token**. Copy the connector URL and the token into Muse's custom
connector, which sends it as `Authorization: Bearer …`. The token is shown once:
the database keeps only its SHA-256 hash (`connector_tokens`). **Replace token**
or **Revoke token** takes effect immediately, with no redeploy. Apply the latest
`supabase/schema.sql` first.

Without a valid token the endpoint rejects every request.
