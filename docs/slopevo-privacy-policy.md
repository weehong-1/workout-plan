# Slopevo Privacy Policy

_Last updated: 28 November 2026_

Slopevo is a personal app for tracking your weight, food photos and daily activity, and for sharing a summary with your trainer. It is run by Vernon Koh, Singapore ("we", "us"). This policy explains what Slopevo collects, where it is kept, who can see it and how to delete it.

## Summary

- Your weigh-ins, food photos and progress photos are stored **on your phone**. They are not uploaded unless you create a trainer link that includes them.
- We use your sign-in details only to run your account.
- WHOOP data is read only after you connect WHOOP, and only to show it to you and in trainer links you create.
- There are no ads, no analytics or tracking tools and no crash-reporting services, and we do not sell your data.
- You can export everything, revoke any trainer link, disconnect WHOOP or delete your account from the app.

## What we collect and why

### 1. Your account
When you sign in we receive your **email address**. If you sign in with Google, we also receive the **name** on your Google account. We use these only to sign you in and to show your name in the app. The sign-in session is kept encrypted on your phone.

### 2. Data kept on your phone
- Weigh-ins (date and weight)
- Food photos and progress photos you take or choose
- Your settings: goal, target weight, step goal, height, units, theme and reminder times

This data stays on your phone. If Android backup is turned on, Android may include it in your device backup to your Google account; that backup is controlled by you and Google, not by us. Your sign-in session is excluded from backups.

Reminders are scheduled on your phone. Slopevo does not use push-notification servers.

### 3. WHOOP data
If you connect your WHOOP account, Slopevo reads the data you allow, through WHOOP's official API:
- cycles (day strain and steps)
- recovery
- sleep
- workouts
- profile (name and email on your WHOOP account)
- body measurements (height, weight and maximum heart rate)

We use this data only to show your activity in the app and in trainer links you create. To keep the connection working, the access tokens WHOOP issues are stored on our server and used only to fetch your data. We never receive your WHOOP password. You can disconnect WHOOP at any time in the app, which deletes the stored tokens. You can also revoke access from your WHOOP account.

### 4. Trainer links
When you create a link for your trainer, Slopevo uploads to our server:
- the **trainer name** you type
- the **period** (a month or a year) and the link's **end date**
- a summary of that period, with only the parts you chose:
  - **Activity:** daily steps, sleep, strain and recovery
  - **Weight:** your weigh-ins, with your first and last progress photo
  - **Food:** your food photos

Anyone with the link can see that summary until it ends or you revoke it, so share it only with your trainer. The link's address is kept only on your phone; our server stores only a scrambled fingerprint of it (a one-way hash), so the address cannot be rebuilt from our records.

When you **revoke** a link, its record and photos are deleted from our server. When a link **expires**, it stops working at once, but its record and photos stay in private storage until you revoke the link or delete your account.

## Who processes your data

We use these providers only to run Slopevo:

| Provider | What it does |
|---|---|
| **Supabase** | Sign-in, sign-in emails, the database and photo storage for trainer links, and WHOOP token storage |
| **Google** | "Sign in with Google", if you use it |
| **Vercel** | Hosts the web page your trainer opens |
| **WHOOP** | Source of your WHOOP data, if you connect it |

Their servers may be in a country other than yours. We do not share your data with anyone else, except when the law requires it.

## Your choices and rights

- **Export:** Profile › Export gives you a ZIP file with your weigh-ins, food log and photos.
- **Revoke a link:** Share with trainer › Revoke.
- **Disconnect WHOOP:** in the app or in your WHOOP account.
- **Delete your account:** Profile › Delete account. This deletes your account, all your trainer links and their photos from our server, then erases Slopevo's data on your phone.
- You can ask us to access, correct or delete your data at vernonweehongkoh.developer@outlook.com.

## Security
Connections use HTTPS. Your sign-in session is encrypted with a key held in your phone's secure hardware. Uploaded photos sit in private storage that only you can read, except through an active trainer link.

## Children
Slopevo is not meant for children under 13, and we do not knowingly collect their data.

## Changes
If we change this policy, we will update the date above and, for important changes, tell you in the app.

## Contact
Vernon Koh, Singapore. vernonweehongkoh.developer@outlook.com
