# Training Plan

A mobile first Next.js app for the "Weekly Training Plan (Push/Pull + Supersets + Arm Day)" Google Sheet.

## Features

* **Next up**: follows the A → B → C rotation from where you left off, and offers optional D Arm Day after C.
* **Custom training plans**: use the dotted **Add Training Plan** card in All days to create a plan with a name and optional description. Plans are saved to your account and show their exercise count (new plans start at zero). Tap a custom plan to update its details; exercise editing is not included yet.
* **Workout screen**: every exercise with warm up, sets × reps, rest, form cues, a tutorial video that plays in a pop up (no new tab), and a one tap swap to the substitute.
* **Supersets** are grouped, so it's clear which exercises to alternate.
* **Set logging**: 2 sets by default, with Add set for more. Pick the weight from a dropdown (your phone's scroll wheel opens next to your last weight; BW and Other… are there too) and tap ✓. No reps entered counts as the target reps.
* **Rest timer** starts on ✓, with ±15s, vibration and a beep. It keeps running if you switch pages or lock the phone.
* **Progression hint** appears when you hit the target reps on every set last time.
* **Keep screen on** toggle, so your phone doesn't lock mid workout (where the browser supports it).
* **Workout history**: finishing a workout saves your sets and returns to the plan. Saved history powers the next-day rotation, previous-set values and progression hints; there is no separate Training Log page or workout CSV export.
* **Body weight**: log your weight each day (kg or lb), see the latest, a trend chart (1M, 3M, 1Y, All) and history, and download it as CSV.
* **Nutrition calculator**: the Nutrition tab shows separate **Workout day** and **Non-workout day** calories, protein, carbs and fat from body weight (kg or lb), activity outside workouts and weight goal. Non-workout maintenance uses 28/33/38 kcal/kg for light/moderate/high daily activity; workout maintenance adds a rough 4 kcal/kg session allowance before applying the goal (−15% loss, unchanged maintenance, +10% gain). Protein and fat stay the same on both days, with extra workout calories allocated to carbs. This is not measured exercise expenditure or automatic workout-log detection. It automatically reads the latest entry from the Weight module and shows its logged date and preferred unit, with an **Update weight** link. There is no duplicate weight input or saving in Nutrition; changing or deleting a Weight entry recalculates targets automatically. Without a logged weight, a single **Log weight** link gets you started. Activity and goal choices are temporary. These adult-only heuristics are explained in the module and are not personalized medical advice. Targets below 1,500 kcal/day are withheld separately for each day, not clamped to a universal minimum. No database migration is needed for this calculator.
* **kg or lb**: every exercise starts in kg; pick lb from its unit dropdown and it's remembered for that exercise.
* Installable to the home screen, with light and dark mode.

Sign-in is required before accessing any application page. Use the email code to sign in; an existing session is restored automatically. **Supabase is the source of truth:** workouts, in-progress drafts (sets, reps, completion, substitutes and notes), body-weight records, unit choices, the rest timer and the keep-screen-on preference are stored in the database. There is no local workout cache, offline mode, backup feature or manual sync control. Only the authentication session is persisted in browser storage.

The app loads the signed-in account's data before showing training pages. Every edit immediately starts an authenticated database write; rapid edits are serialized in memory to preserve their order. Saving indicators and failures are visible, and Save/Finish success is shown only after database confirmation. If a write fails, keep the page open and use **Retry saving** (reconnecting also retries). Unsaved edits are held only in memory and are lost if the page is closed; the browser warns before leaving while writes are outstanding. Sign-out waits for writes and refuses to proceed if they fail.

Reloading or signing in on another device loads the latest database records. Simultaneous edits to the same draft are last-write-wins; this is not a live collaborative editor. Temporary UI state such as open dialogs, chart filters and unsubmitted form fields is not account data. Static plan and guidance content remain in the source code. If Supabase is not configured or account data cannot load, access stays blocked.

The sign-in screen is a client-side application gate, not server-side authorization for static plan content. Supabase row-level security protects each account's stored data.

### Delete an account

In **Account → Delete account…**, review the warning, type `DELETE`, then choose **Delete permanently**. This removes the signed-in Supabase Auth user and their workout history, drafts, body weights, custom plans, added exercises, preferences and mutation receipts in one database transaction, then signs out this browser. Other accounts are untouched. Deletion cannot be undone; signing up again creates an empty account. Other devices cannot refresh the deleted user's session; already-issued access tokens can remain valid until expiry, but the deleted user's records are gone and foreign keys prevent new records for that user. Provider-managed backups follow the provider's retention policy.

**Deployment:** rerun the updated [schema](<supabase/schema.sql>) in the Supabase SQL Editor before deploying this feature. It installs the authenticated-only `delete_own_account` function (`SECURITY DEFINER`, empty search path), validates the caller's own user ID and exact confirmation, and never accepts a target account to delete. No service-role key or additional environment variable is required. Without this migration, deletion shows an explicit setup error instead of claiming success.

## Supabase setup (required database and authentication)

**Existing installations must rerun the updated [schema](<supabase/schema.sql>) before deploying.** It preserves existing log, weight and settings rows, adds workout drafts, custom training plans and timer/preferences columns, and installs the authenticated transactional write function. Existing database records remain available. Legacy phone-only records are not automatically imported or deleted; export any needed local records from the old version before upgrading.

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
