# Setting up the WHOOP connection

Do this once in the Supabase dashboard. You need two files from this folder.

## 1. Tables

Open **SQL Editor**, then **New query**. Paste all of `whoop.sql` and click **Run**.

This creates two tables:

- `whoop_connections`: the account's WHOOP tokens.
- `whoop_oauth_states`: short-lived "Connect" requests.

Neither the app nor the website can read these tables. Only the function below can.

## 2. Secrets

Under **Edge Functions › Secrets**, you should already have:

- `WHOOP_CLIENT_ID`
- `WHOOP_CLIENT_SECRET`

Supabase provides `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` to functions itself, so you don't add those.

## 3. Function

1. Go to **Edge Functions › Deploy a new function › Via Editor**.
2. Name it exactly `whoop-callback`. That matches the redirect URL registered with WHOOP.
3. Replace the editor's code with all of `functions/whoop-callback/index.ts`.
4. Click **Deploy**.
5. Open the function's **Settings**, turn **Verify JWT** OFF, and save.

Verify JWT has to be off because WHOOP's redirect carries no Supabase sign-in. The function checks the app's sign-in itself.

## 4. Try it

In Slopevo, open **Profile › WHOOP › Connect**. Sign in to WHOOP and tap **Allow**. You come back to the app and see "WHOOP connected", and Activity switches from sample data to your WHOOP data.

If it fails, the function's **Logs** tab shows why.

## Updating the function later

Edit `index.ts` in the repository, then paste it into the dashboard editor again and deploy.
