# Workout plan MCP

A local MCP server that lets Claude read and log your workouts and body weight.
It signs in with your email and password, so it can only see your own data,
and it saves through the same database function as the app.

## Tools

| Tool | What it does |
| --- | --- |
| `get_body_weight` | Weigh-ins grouped by week: weekly average, change from the week before, and whether weight is dropping over the last 4 weeks |
| `log_body_weight` | Save a weigh-in (replaces that day's if there is one) |
| `delete_body_weight` | Delete a day's weigh-in and its photos |
| `get_plan` | Each day's exercises with sets, reps and the suggested weight for next time, plus the next day in the rotation |
| `get_workouts` | Saved workouts, newest first |
| `get_exercise_history` | Every session of one exercise and the next suggested weight |
| `log_workout` | Save a finished workout for a plan day |
| `delete_workout` | Delete a saved workout and its photos |

## Setup

Needs Node 23.6 or later. It loads the app's TypeScript logic directly.

1. Install the dependencies:

   ```powershell
   cd mcp; npm install
   ```

2. Copy `.env.example` to `.env` and fill in your sign-in email and password.
   `.env` is git-ignored. The Supabase URL and key come from `../.env.local`.

3. Check it signs in. This only reads data:

   ```powershell
   node mcp/smoke.mjs
   ```

4. Add it to Claude Code (use your own path):

   ```powershell
   claude mcp add --scope user workout-plan -- node --no-warnings C:/Users/Vernon/Projects/workout-plan/mcp/server.mjs
   ```

   Or to Claude Desktop, in `claude_desktop_config.json`:

   ```json
   {
     "mcpServers": {
       "workout-plan": {
         "command": "node",
         "args": ["--no-warnings", "C:/Users/Vernon/Projects/workout-plan/mcp/server.mjs"]
       }
     }
   }
   ```

Then ask things like "Is my weight dropping?", "What's my next workout?" or
"Log 92.4 kg for today".
