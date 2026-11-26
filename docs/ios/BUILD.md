# Building the iOS app on a Mac

The Xcode project is not in git. It is generated from `ios/project.yml` with
XcodeGen, so the project file never needs to be merged.

## Requirements

* A Mac with **macOS Tahoe 26.6 or later** (Xcode 27 needs it).
* **Xcode 27** from the Mac App Store, opened once so it installs its components.
* An Apple ID signed in under Xcode › Settings › Accounts. A free Personal Team
  is enough to run on your own iPhone (the app then expires after 7 days); the
  paid Apple Developer Program is needed for TestFlight.
* [Homebrew](https://brew.sh).
* The iPhone on iOS 27 with Developer Mode on (Settings › Privacy & Security ›
  Developer Mode; the option appears after the iPhone is first connected to
  Xcode).

## Steps

1. Install XcodeGen:

   ```sh
   brew install xcodegen
   ```

2. Clone the repo and switch to the `ios` branch:

   ```sh
   git clone git@github.com:weehong-1/workout-plan.git
   cd workout-plan
   git checkout ios
   ```

3. Generate the project:

   ```sh
   cd ios
   xcodegen generate
   ```

   This writes `ios/WorkoutPlan.xcodeproj` and `ios/WorkoutPlan/Info.plist`
   (both ignored by git). Run it again after every pull that adds or removes
   files, or changes `project.yml`.

4. Open the project:

   ```sh
   open WorkoutPlan.xcodeproj
   ```

5. Set the signing team: select the **WorkoutPlan** project in the navigator,
   then the **WorkoutPlan** target › **Signing & Capabilities**. Keep
   *Automatically manage signing* on and choose your team under **Team**.
   If Xcode says the bundle id `com.example.workoutplan` is taken, change it
   to something unique, for example `com.<yourname>.workoutplan`, in
   `project.yml` (`PRODUCT_BUNDLE_IDENTIFIER`) and run `xcodegen generate`
   again. Changes made only in Xcode are lost on the next generate.

6. Check the HealthKit capability: on the same **Signing & Capabilities** tab,
   **HealthKit** must be listed. It comes from
   `ios/WorkoutPlan/Resources/WorkoutPlan.entitlements`. Clinical Health
   Records and Background Delivery stay off.

7. Run on the iPhone: connect it with a cable (or pair it over Wi-Fi under
   Window › Devices and Simulators), trust the Mac on the iPhone, choose the
   iPhone as the run destination in the toolbar and press **Run** (⌘R).
   The first time, the iPhone may ask you to trust the developer under
   Settings › General › VPN & Device Management.

8. On first launch: sign in with your website email (or tap **Not Now**),
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
4. **App config.** In `ios/project.yml`, replace the three placeholders under
   the WorkoutPlan target's settings, using host names only (no `https://`):

   ```yaml
   SUPABASE_HOST: abcdefgh.supabase.co
   SUPABASE_PUBLISHABLE_KEY: sb_publishable_...
   WEB_APP_HOST: your-site.vercel.app
   ```

   These are the same public values as the website's
   `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`;
   never use a secret or service_role key. Then run `xcodegen generate` again.
   While the Supabase values are placeholders the app hides sign-in; while
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
* **Signing errors after a pull**: run `xcodegen generate` again and re-check
  the team (step 5).
