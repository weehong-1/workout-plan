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

Logged data is stored in the browser's localStorage on your phone.

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
