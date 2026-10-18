# Training Plan

A mobile first Next.js app for the "Weekly Training Plan (Push/Pull + Supersets + Arm Day)" Google Sheet.

## Features

* **Next up**: follows the A → B → C rotation from where you left off, and offers optional D Arm Day after C.
* **Workout screen**: every exercise with warm up, sets × reps, rest, form cues, a tutorial video that plays in a pop up (no new tab), and a one tap swap to the substitute.
* **Supersets** are grouped, so it's clear which exercises to alternate.
* **Set logging**: 2 sets by default, with Add set for more. Pick the weight from a dropdown (your phone's scroll wheel opens next to your last weight; BW and Other… are there too) and tap ✓. No reps entered counts as the target reps.
* **Rest timer** starts on ✓, with ±15s, vibration and a beep. It keeps running if you switch pages or lock the phone.
* **Progression hint** appears when you hit the target reps on every set last time.
* **Keep screen on** toggle, so your phone doesn't lock mid workout (where the browser supports it).
* **Training Log**: past sessions, plus "Copy for Sheets", which pastes straight into the Training Log tab (Date, Day, Exercise, Set 1, Set 2, Notes), and CSV download.
* **Body weight**: log your weight each day (kg or lb), see the latest, a trend chart (1M, 3M, 1Y, All) and history, and download it as CSV.
* **Notes**: all the rules from the Notes tab.
* **kg or lb**: every exercise starts in kg; pick lb from its unit dropdown and it's remembered for that exercise.
* Installable to the home screen, with light and dark mode.

Everything is saved on your phone first, so the app is instant and keeps working with a bad gym connection. Sign in (Backup & sync, at the bottom of the home screen) to also save it to Supabase and use it on any device. The first sign in uploads what's already on the phone.

## Supabase setup (backup & sync)

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

Supabase's built in email only sends a few emails an hour, to your own team's addresses, which is fine for a personal app. Without the two variables the app runs on the phone only, as before.

## Updating the plan

The plan and notes live in `src/lib/plan.ts` and `src/lib/notes.ts`. Edit them when the sheet changes.

## Run locally

```bash
npm install
npm run dev
```

## Deploy

Push to GitHub and import the repo on [Vercel](https://vercel.com/new). No environment variables are needed.

On iPhone, open the site in Safari, tap Share, then tap **Add to Home Screen**. On Android, open it in Chrome, open the menu, then tap **Add to Home screen**.
