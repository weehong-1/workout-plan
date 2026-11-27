# Building the iOS app on a Mac

The Xcode project `ios/WorkoutPlan.xcodeproj` is in git. Open it and build;
no generator is needed. Project settings are changed in Xcode and committed.

## Requirements

* A Mac with **macOS Tahoe 26.6 or later** (Xcode 27 needs it).
* **Xcode 27** from the Mac App Store, opened once so it installs its components.
* An Apple ID signed in under Xcode › Settings › Accounts. A free Personal Team
  is enough to run on your own iPhone (the app then expires after 7 days); the
  paid Apple Developer Program is needed for TestFlight.
* The iPhone on iOS 27 with Developer Mode on (Settings › Privacy & Security ›
  Developer Mode; the option appears after the iPhone is first connected to
  Xcode).

## Steps

1. Put the repo on the Mac's own disk, on the `ios` branch. Building from a
   folder shared from a Linux VM (such as `~/OrbStack/...`) is slow and can
   fail. Cloning from that folder works:

   ```sh
   git clone -b ios ~/OrbStack/vitrack/home/vernon/projects/workout-plan ~/Developer/workout-plan
   ```

2. Open the project:

   ```sh
   open ~/Developer/workout-plan/ios/WorkoutPlan.xcodeproj
   ```

   New Swift files must be added to the **WorkoutPlan** target in Xcode
   (File › Add Files, or create them in Xcode), or they are not compiled.

3. Set the signing team: select the **WorkoutPlan** project in the navigator,
   then the **WorkoutPlan** target › **Signing & Capabilities**. Keep
   *Automatically manage signing* on and choose your team under **Team**.
   If Xcode says the bundle id `com.example.workoutplan` is taken, change it
   to something unique, for example `com.<yourname>.workoutplan`, under
   **Bundle Identifier** on the same tab.

4. Check the HealthKit capability: on the same **Signing & Capabilities** tab,
   **HealthKit** must be listed. It comes from
   `ios/WorkoutPlan/Resources/WorkoutPlan.entitlements`. Clinical Health
   Records and Background Delivery stay off.

5. Run on the iPhone: connect it with a cable (or pair it over Wi-Fi under
   Window › Devices and Simulators), trust the Mac on the iPhone, choose the
   iPhone as the run destination in the toolbar and press **Run** (⌘R).
   The first time, the iPhone may ask you to trust the developer under
   Settings › General › VPN & Device Management.

6. On first launch: sign in with your website email (or tap **Not Now**),
   read the Why Health screen, tap **Continue** and turn on all categories in
   the Health sheet. If iOS asks how much history to share, choose
   **All Recorded Data**.

## Server for trainer links (optional)

Everything except sign-in and **Share with trainer** works without this. It
uses the web app's existing Supabase project and Vercel site (spec D10).

1. **Database.** In Supabase › SQL Editor, run the whole of
   `supabase/schema.sql` again. It is safe to rerun; it adds the
   `share_links` table, the `get_shared_review` function and the
   "Shared photos" storage policy.
2. **Web page.** Deploy this branch's web app to Vercel (or merge it), so
   `https://<your site>/share/<token>` exists. No new environment variables
   are needed.
3. **Email code.** Supabase › Authentication › Email templates: the
   "Magic Link" template must include `{{ .Token }}` so the email shows the
   code the app asks for. The website's code sign-in already relies on this.
4. **App config.** In Xcode, select the **WorkoutPlan** target › **Build
   Settings**, and under **User-Defined** set these three values, using host
   names only (no `https://`, because `//` starts a comment in build settings):

   ```yaml
   SUPABASE_HOST: abcdefgh.supabase.co
   SUPABASE_PUBLISHABLE_KEY: sb_publishable_...
   WEB_APP_HOST: your-site.vercel.app
   ```

   These are the same public values as the website's
   `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`;
   never use a secret or service_role key. While the Supabase values are placeholders the app hides sign-in; while
   the web host is a placeholder it hides only sharing.

## Simulator

Debug builds on the simulator use built-in sample data instead of HealthKit
(`HealthStoreFactory`), because the simulator has no Apple Watch data. On a
real iPhone the app always reads Apple Health.

## Troubleshooting

* **"No data yet" on the iPhone**: open Settings › Privacy & Security ›
  Health › Workout Plan and check that every category is on.
* **Banner "Only 30 days of history shared"**: on the same screen, change the
  history option to All Recorded Data.
* **Signing errors after a pull**: re-check the team (step 3). The team is
  stored in the project file, so a pull from someone using another team
  changes it.
