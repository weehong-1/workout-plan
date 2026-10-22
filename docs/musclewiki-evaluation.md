# MuscleWiki API evaluation

Evaluated: 21 October 2026. Scope: this repository's current authored training plan, including every substitute. Research only; no application integration or exercise/video replacement was performed.

## Decision

**Potentially suitable as a paid exercise-instruction/video provider, but complete exercise coverage is NOT yet established. Do not approve a full replacement on the available evidence.**

The official documentation and API schema are public. An initial anonymous request to `https://api.musclewiki.com/exercises?limit=1` returned HTTP 401 with `{"detail":"Authentication required","message":"Missing X-API-Key header"}`.

### Authenticated re-evaluation

After the user configured a key and authorized its use, the key was loaded securely from the local environment file without displaying its value. A single authenticated `GET /exercises?limit=1` request returned **HTTP 403**, with:

```json
{
  "detail": "BASIC tier is restricted to playground access only. Upgrade to TESTING ($10/mo) or higher for direct API access.",
  "message": "BASIC tier is restricted to playground access only. Upgrade to TESTING ($10/mo) or higher for full API access.",
  "upgrade_url": "https://api.musclewiki.com/dashboard/billing"
}
```

**The configured key is subject to BASIC-tier restrictions, so it cannot currently be used for this programmatic audit.** This is a subscription permission restriction, not evidence that any exercise is missing. No further catalog requests were made after this denial; no Playground-only restriction was bypassed. No account registration, plan change, subscription purchase, or media download was performed.

Consequently, every exercise below is **unverified**, not missing. No reliable coverage percentage, missing-exercise count, or confirmed provider-ID mapping can be reported yet. Documentation examples and the vendor's advertised database size are not evidence of this plan's exact coverage.

## Local inventory

Source: [plan.ts](../src/lib/plan.ts#L48-L538).

| Metric | Count |
| --- | ---: |
| Exercise slots across days A–D | 28 |
| Day A / B / C / D slots | 6 / 6 / 9 / 7 |
| Unique main exercise names | 26 |
| Unique substitute names | 24 |
| Names shared between main and substitute roles | 4 |
| **Unique exercises requiring verification** | **46** |

Shared names: Dumbbell Curl, Seated Cable Row, Leg Press, Overhead Dumbbell Triceps Extension. Repeated main slots are Dumbbell Lateral Raise and Standing Calf Raise. Optional Arm Day is included.

## Complete verification checklist

**All 46 rows currently have status UNVERIFIED.** The proposed queries are search starting points, not observed MuscleWiki exercise titles or confirmed equivalents. Actual details, equipment, instructions, stance/grip and playable video must be checked before accepting a match. Do not infer an exercise is absent from a failed exact-name search, since the API also searches instructions and has relevance-ranked results.

| # | Local exercise | Role | Suggested query / matching concern |
| ---: | --- | --- | --- |
| 1 | Barbell Back Squat | Main | `barbell squat`; verify back squat, not front squat |
| 2 | Barbell Bench Press | Main | `barbell bench press`; flat bench |
| 3 | Overhead Press | Main | `barbell overhead press`, `military press`; verify implement and standing execution from current tutorial |
| 4 | Standing Calf Raise | Main | `standing calf raise`; confirm bodyweight, machine, or loaded variant against current tutorial |
| 5 | Bulgarian Split Squat | Main | `bulgarian split squat`; verify rear-foot elevation and loading |
| 6 | Dumbbell Lateral Raise | Main | `dumbbell lateral raise` |
| 7 | Romanian Deadlift | Main | `barbell romanian deadlift`; cues mention a bar; do not accept conventional deadlift |
| 8 | Pull Up | Main | `pull up`, `pullup`; verify grip and exclude chin-up/assisted-only variation |
| 9 | Barbell Bent Over Row | Main | `barbell bent over row`; verify grip and torso position |
| 10 | Lying Leg Curl | Main | `lying leg curl`; prone machine, not seated curl |
| 11 | Face Pull | Main | `cable face pull`; rope toward forehead |
| 12 | Dumbbell Curl | Both | `dumbbell curl`; verify wrist rotation and stance |
| 13 | Incline Dumbbell Press | Main | `dumbbell incline bench press`; incline, not flat |
| 14 | Seated Cable Row | Both | `cable seated row`; verify handle/grip |
| 15 | Leg Press | Both | `leg press`; verify machine configuration |
| 16 | Barbell Hip Thrust | Main | `barbell hip thrust`; bench-supported, not floor glute bridge |
| 17 | Overhead Dumbbell Triceps Extension | Both | `dumbbell overhead tricep extension`; verify one/two hands and stance |
| 18 | Cable Fly | Main | `cable chest fly`; verify pulley height/path |
| 19 | Kneeling Cable Crunch | Main | `cable kneeling crunch`; kneeling, not standing |
| 20 | EZ Bar Curl | Main | `ez bar curl`; distinguish EZ bar from straight bar |
| 21 | Close Grip Bench Press | Main | `barbell close grip bench press` |
| 22 | Incline Dumbbell Curl | Main | `dumbbell incline curl`; arms hanging behind torso |
| 23 | Overhead Cable Triceps Extension | Main | `cable overhead tricep extension`; face away, verify pulley/rope setup |
| 24 | Reverse Grip Curl | Main | `reverse curl`; overhand grip, verify implement from current tutorial |
| 25 | Bent Over Reverse Dumbbell Fly | Main | `dumbbell rear delt fly`, `reverse fly`; bent-over, not incline chest-supported |
| 26 | Farmer's Walk | Main | `dumbbell farmer carry`, `farmers walk`; walking two-dumbbell carry, not static hold |
| 27 | Dumbbell Bench Press | Substitute | `dumbbell bench press`; flat bench |
| 28 | Seated Dumbbell Shoulder Press | Substitute | `seated dumbbell shoulder press`; seated, not standing |
| 29 | Single Leg Dumbbell Calf Raise | Substitute | `dumbbell single leg calf raise`; unilateral loaded variant |
| 30 | Smith Machine Split Squat | Substitute | `smith machine split squat`; distinguish ordinary split squat from rear-foot-elevated version |
| 31 | Cable Lateral Raise | Substitute | `cable lateral raise`; verify unilateral setup |
| 32 | Dumbbell Romanian Deadlift | Substitute | `dumbbell romanian deadlift`; bilateral hip hinge |
| 33 | Lat Pulldown | Substitute | `lat pulldown`; verify grip and cable/machine variant |
| 34 | Stability Ball Leg Curl | Substitute | `stability ball hamstring curl`, `swiss ball leg curl`; ball variant, not machine curl |
| 35 | Reverse Pec Deck Fly | Substitute | `machine reverse fly`, `reverse pec deck`; rear-delt machine movement |
| 36 | Cable Curl | Substitute | `cable curl`; verify handle, stance and pulley height |
| 37 | Machine Chest Press | Substitute | `machine chest press`; verify machine and press angle |
| 38 | Single Arm Dumbbell Row | Substitute | `dumbbell single arm row`; verify bench support |
| 39 | Goblet Squat | Substitute | `goblet squat`; confirm implement from current tutorial |
| 40 | Smith Machine Hip Thrust | Substitute | `smith machine hip thrust`; Smith, not barbell or dedicated hip-thrust machine |
| 41 | Rope Pushdown | Substitute | `cable rope tricep pushdown`; rope, not straight bar |
| 42 | Pec Deck Fly | Substitute | `machine chest fly`, `pec deck`; chest fly, not rear-delt fly |
| 43 | Hanging Leg Raise | Substitute | `hanging leg raise`; hanging straight-leg movement, not captain's-chair knee raise |
| 44 | Parallel Bar Dips | Substitute | `parallel bar dips`; not bench dips; check torso/elbow position |
| 45 | Hammer Curl | Substitute | `dumbbell hammer curl`; neutral grip |
| 46 | Kettlebell Farmer's Walk | Substitute | `kettlebell farmer carry`, `kettlebell farmers walk`; two kettlebells, not unilateral suitcase carry |

Priority ambiguity checks: Standing Calf Raise, Smith Machine Split Squat, Single Leg Dumbbell Calf Raise, Stability Ball Leg Curl, and the distinct dumbbell/kettlebell walking carries. These are **matching risks**, not known catalog gaps. The existing YouTube demonstrations have not been watched in this evaluation; exact technique ambiguities remain open.

## Verified API capabilities and access constraints

Official sources: [documentation](https://api.musclewiki.com/documentation), [OpenAPI schema](https://api.musclewiki.com/openapi.json), [pricing](https://api.musclewiki.com/pricing).

- Official base URL: `https://api.musclewiki.com`; authenticate server-side with `X-API-Key`.
- Vendor advertises 1,900+ exercises with video demonstrations. This is a vendor claim, not an inventory independently queried in this audit.
- `GET /exercises`: paginated ID/name results in `results`, with `total`, `limit`, `offset`, and `count`; maximum page size 100.
- `GET /search?q=...`: relevance-ranked search over names and instructions, with equipment and muscle filters.
- `GET /exercises/{id}`: instructions, equipment, muscles and video variants. Schema permits null video URLs; verify at least one actual usable video per selected match.
- `GET /exercises/{id}/videos`: video variants only. Do not construct or guess media filenames.
- `GET /filters`: runtime canonical filter values. Some documentation examples differ from the schema; verify the actual response contract during the pilot.

| Plan | Published monthly price | Calls/month | Relevant access |
| --- | ---: | ---: | --- |
| BASIC | Free | 500 | Signed-in Playground only; not usable by this app |
| TESTING | $10 | 1,000 | Direct API; English exercises and videos; suitable for evaluation |
| GROWTH | $39.99 | 30,000 | Exercises/videos, multilingual; production candidate |
| PROFESSIONAL | $99.99 | 100,000 | Adds routines, workouts, bodymaps, secondary/tertiary muscles |
| ENTERPRISE | $199.99 | 300,000 | Higher quota and priority support |

Prices are published prices observed on the evaluation date, not a purchase quote. All paid plans include the exercise/video catalog and commercial use within the API terms. This app already authors its own programs, so vendor routines are not required just to replace tutorial content. Select a production quota only after measuring real usage.

## Integration implications for this app

1. **Keep authored plans and existing exercise names.** [plan.ts](../src/lib/plan.ts#L4-L16) stores programming and tutorial URLs. Exercise names are also used in [workout logging](../src/components/Workout.tsx) and [per-exercise history](../src/lib/store.ts). Use a separate curated provider-ID mapping instead of renaming local exercises to vendor names. Do not replace prescribed sets/reps/rest with provider defaults.
2. **A video-player change is needed.** [VideoModal.tsx](../src/components/VideoModal.tsx#L60-L76) currently embeds YouTube only. MuscleWiki streams need a native player and authenticated media flow, not simply a new URL in the existing iframe.
3. **Keep the permanent API key server-only.** Never use a `NEXT_PUBLIC_` variable. Any future server routes need real app authorization/rate limiting; the current client-side UI gate alone does not secure a backend quota-bearing proxy.
4. **Use media tokens or a transparent streaming proxy.** Documentation supports backend-minted short-lived media tokens, default lifetime 900 seconds, for a normal `<video>` URL. Tokens are credentials: avoid logging or analytics capture. Alternatively proxy without storing video bytes and preserve upstream headers/Range behavior.
5. **Measure streaming cost, not just metadata requests.** Each `/stream/` request, including each Range request, counts as an API call. A video play can cost multiple calls. Lazy-load media and test on target mobile browsers. A 1,000-call plan is not 1,000 video plays.
6. **Retain fallbacks.** Quota exhaustion returns 429; plan restrictions 403; missing/invalid credentials 401. Preserve existing tutorial links and authored text for unmatched exercises, removed content and outages. Do not block logging on API availability.

## License and offline constraints

Controlling source: [API Terms of Use](https://api.musclewiki.com/api-terms), last updated October 8, 2026. Also consult the [media documentation](https://api.musclewiki.com/documentation#media-tokens).

- Text metadata cache: at most **30 days**, not a permanent imported provider dataset.
- Videos: **transient playback buffers only**. No downloads, offline media library, disk/object-storage copies, or shared/CDN video cache.
- No scraping/harvesting/bulk export of videos, images, or video URLs. A targeted evaluation of this app's 46 exercises is preferable to any full-database media harvest.
- Transparent backend streaming is permitted under the stated restrictions; preserve upstream response headers including `Cache-Control: no-store`.
- Terms allow thumbnails/bodymaps caching up to 24 hours, while documentation is stricter for bodymaps and shared caches. Follow the stricter no-store/response-header behavior and obtain written clarification before caching bodymaps.
- Required legal disclosure: **“Exercise data and videos provided by MuscleWiki.com”** in the app's Terms, Privacy Policy or equivalent legal document.
- Embedded branding must remain visible and unaltered. A visible linked in-app credit is encouraged, not mandatory.
- Content and endpoints can be removed. Paid access is not permission to permanently mirror the catalog or expose an unrestricted third-party API.

## Next evaluation step — before implementation

Choose either:

- **Free manual evaluation:** sign into the official [Playground](https://api.musclewiki.com/playground) with BASIC and check the 46 rows; share permitted text results (ID/name/equipment/instructions and whether a video exists), not a bulk media export.
- **Programmatic evaluation:** the currently configured key returned a BASIC-tier denial. Upgrade its account to TESTING or higher through the [billing dashboard](https://api.musclewiki.com/dashboard/billing), or configure another key with paid direct access; then run targeted searches and inspect relevant exercise details. This requires the user's subscription decision; no purchase has been made. Do not paste the secret into source code or a public chat.

For each row, record one of: exact match, reviewed equivalent name, different/ambiguous variant, or absent after broader searches. Record selected provider ID and verify playback for the intended gender/angle separately. Inspect actual demonstrations for ambiguous movements. Do not count an unreviewed fuzzy candidate as covered.

**Go/no-go criteria:** all 46 local movements mapped to reviewed equivalents with usable videos, or explicitly accepted exceptions retaining current tutorials; acceptable measured streaming quota/cost; server-side key protection; compliant caching/attribution; and working failure fallbacks. Until then, the coverage evaluation remains incomplete and implementation should wait.
