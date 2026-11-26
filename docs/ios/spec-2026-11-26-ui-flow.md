# iOS app: UI and flow

Date: 26 Nov 2026 · Branch: `ios` (from `master` at 572ee85) · Status: draft for review

Personal project, not Resetrix. This file is the record of decisions for the iOS app. Anything marked **Proposed** is a suggestion by Claude that Vernon has not confirmed yet; anything marked **Open** still needs a decision.

## 1. Scope (Vernon, 26 Nov 2026)

* Turn the app into an iOS app. The workout module is dropped for now.
* The app tracks only activity level, body weight and food intake photos.
* Four modules:
  1. **Activity level.** Layout screenshot to be provided later.
  2. **Daily weight measurements.** A photo is optional, to track progress visually.
  3. **Food intake photos.** All meals are combined into one daily intake. Photos do not need a meal time; they can be organised with labels.
  4. **Sharing with an external party** (for example a trainer) for a monthly or yearly review.
* Sleep and activity from Vernon's Apple Watch come in through Apple Health.
* **Open:** the nutrition calculator in the web app depends on workout days. Assumed dropped with the workout module; not confirmed.

## 2. Decisions

| # | Decision | Source |
|---|---|---|
| D1 | Native SwiftUI app built on the iOS 27 design system (Liquid Glass) as the main library | Vernon, 26 Nov 2026 |
| D2 | Work happens on Windows, so for now only documentation and design. Building the app later needs a Mac with Xcode | Vernon, 26 Nov 2026 |
| D3 | iOS documents live on branch `ios` of this repo, under `docs/ios/` | Vernon, 26 Nov 2026 |
| D4 | Screens are drawn on a Claude Design canvas as a preview; its source is kept in this repo | Vernon, 26 Nov 2026 |
| D5 | The trainer sees the shared data through a link. An expiry can be set or left off | Vernon, 26 Nov 2026 |

## 3. Design rules for iOS 27

* Use the system components: `TabView` tab bar, navigation toolbars, sheets. Standard SwiftUI components pick up the iOS 27 Liquid Glass look, including the system Liquid Glass slider, without extra work. A custom tab bar does not, so the bottom bar in the reference design is replaced by the system tab bar.
* Keep from the reference design: the coloured metric cards, rings and bar charts. They are the content under the glass.
* The app must stay readable at every Liquid Glass slider setting and with Reduce Transparency and Increase Contrast on.
* **Proposed:** charts with Swift Charts; minimum iOS version 27, since the app is for Vernon's own iPhone.

## 4. Screen map

```
First launch:  Sign in → Why Health (explainer) → Apple Health prompt → Units (kg or lb)
                                                                           ↓
Tab bar:       Activity        Weight            Food               Review
                  ↓               ↓                 ↓                   ↓
Opens:         Metric detail   Log weigh-in      Add food photo     Trainer link
                               (sheet)           (camera / sheet)   (sheet)
Account:       avatar in the top bar of each tab
```

## 5. First launch

1. **Sign in.** Email code, as in the web app. **Open:** whether to add Sign in with Apple.
2. **Why Health.** An app screen shown before the system prompt. It lists what is read (steps, active calories, exercise minutes, heart rate, sleep, Watch workouts) and why, and recommends sharing all recorded data because the yearly review needs history.
3. **Apple Health prompt** (system). Read access per data type. iOS 27 reportedly adds a second step that asks how much history to share: "Past 30 Days and Future Data" or "All Recorded Data and Future Data". Source: Apple Developer Forums thread 842499, a developer report, not Apple documentation; check on a device.
4. **Units.** kg or lb.

Camera and photo library permissions are asked only when first used.

## 6. Tabs

### 6.1 Activity

* Today's cards from Apple Health: steps, sleep, heart, exercise minutes, active calories, Watch workouts (for example walking or cycling). Read only; nothing is logged in the app.
* Tap a card for its detail: Day, Week, Month and Year views with a chart and key figures.
* Updates when Apple Health gets new data from the Watch; pull to refresh.
* Empty states: no Watch data yet; Health access not given.
* If only 30 days of history were shared, a banner explains how to change it in Settings › Privacy & Security › Health.
* **Open:** final card set and layout, waiting for Vernon's screenshot.

### 6.2 Weight

* Top: latest weight and change against last week, then a trend chart (Week, Month, Year).
* History list, newest first, with photo thumbnails.
* **+** opens the Log weigh-in sheet: weight, date (today by default), optional photos from camera or library, Save. One entry per day; logging a date that already has an entry edits it.
* Tap an entry to edit or delete it.
* **Open:** whether weigh-ins are also written to Apple Health.
* **Proposed:** a side-by-side view of two dates' photos to compare progress.

### 6.3 Food

* Top: date strip to move between days.
* The day's intake as one photo grid, ordered by the time each photo was taken.
* Label chips filter the grid: All plus the labels.
* **+** opens the camera straight away; the photo library is one tap away. Adding a label or note is optional.
* Tap a photo for full screen, to change its label or delete it.
* **Open:** fixed labels (Breakfast, Lunch, Dinner, Snack) or labels Vernon creates.

### 6.4 Review

* Pick a month or a year.
* Summary: activity and sleep averages, weight trend with the first and last photo of the period, number of days with food photos.
* **Share with trainer** opens the Trainer link sheet (section 7).
* List of active links with their period and expiry; each can be revoked.

### 6.5 Account

Avatar in the top bar of every tab: units, Health access status, sign out, delete account.

## 7. Trainer link

* Vernon creates a link: chooses the period (a month or a year), what it includes (activity, weight, weight photos, food photos) and, optionally, an expiry date.
* Before the link is created, a consent screen says that Apple Health data will leave the phone and be readable by anyone who has the link.
* The trainer opens the link in a browser; no app or account needed. It shows the review for the chosen period, read only.
* Vernon can revoke any link at any time, expiring or not.
* **Open:** a link without expiry stays readable by anyone it is forwarded to until revoked. Decide whether such links need a PIN or a default expiry.
* **Open:** who hosts the trainer page (the existing web app or a new page).

## 8. Data

* Activity, sleep and heart data stay in Apple Health. The app reads them on the phone. Only the summaries included in a trainer link are copied to the server, after consent.
* Weight entries, weight photos and food photos are stored in the backend.
* **Open:** keep the existing Supabase project (weight history and photos carry over) or start a new one.

## 9. Next steps

1. Vernon reviews this file.
2. Draw the screens on a Claude Design canvas (D4) and keep the source in this repo.
3. Activity layout once Vernon sends the screenshot.
