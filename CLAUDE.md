# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A dashboard for Sleeper fantasy football leagues: a React frontend and a Spring Boot
backend that proxies/aggregates data from the public [Sleeper API](https://docs.sleeper.com).
Monorepo with `backend/` and `frontend/` as independent, separately-run projects.

Sleeper gives each season of a league its own league id (no id spans years), so the app
models a **league family** — a stable app-level `key` (e.g. `depot`) covering several
Sleeper league ids, one per season — rather than treating a Sleeper league id as the
top-level concept. This is what most of the domain model below is structured around.

## Commands

**Backend** (`backend/`, Java 21, Gradle wrapper — no local Gradle install needed):
```
./gradlew bootRun        # run on http://localhost:8080
./gradlew build          # compile + test + package
./gradlew test           # run tests — pure-function unit tests, no Spring context needed
```

**Frontend** (`frontend/`, React + Vite + TypeScript):
```
npm install
npm run dev               # http://localhost:5173, proxies /api/* to localhost:8080
npm run build              # tsc -b && vite build -> frontend/dist
npm run lint                # oxlint
npm run test                 # vitest run — pure-function unit tests (frontend/src/api/aggregations.test.ts)
```

There's no single top-level command — backend and frontend are built/run separately.

## Architecture

**Backend is a thin proxy/aggregator, not a data owner. No database** — see "Why no
database" below; this is a config + in-memory caching problem instead.

**Two layers do the work, split by scope:**
- [SeasonDataService](backend/src/main/java/com/ffdash/league/SeasonDataService.java) —
  one Sleeper league id in, one [SeasonSummary](backend/src/main/java/com/ffdash/league/SeasonSummary.java)
  out. Calls three Sleeper endpoints via [SleeperClient](backend/src/main/java/com/ffdash/sleeper/SleeperClient.java)
  (`/league/{id}`, `/league/{id}/rosters`, `/league/{id}/users`), joins rosters to users by
  `owner_id`, ranks teams (wins desc, then points desc — the only ranking signal Sleeper's
  API gives us), and **caches** the result (see below). This is where a single season's data
  is assembled; it has no concept of "family" or history.
- [LeagueService](backend/src/main/java/com/ffdash/league/LeagueService.java) — orchestrates
  across families/seasons using `SeasonDataService`: `getFamilyHistory(key)` assembles one
  family's full season list, `getOwnerCareerSummaries()` fetches *every* family's *every*
  season and aggregates per Sleeper user (`user_id`, stable across leagues for the same
  person — this is what makes cross-league aggregation possible without a database).
  Each per-season fetch is wrapped in try/catch-and-skip so one bad/unreachable league id
  degrades that one season out of the response instead of 500ing the whole request —
  important once a single request (`/api/owners` especially) fans out to many Sleeper calls.

**Badge eligibility lives in its own package**, `com.ffdash.league.badge`
([BadgeEligibility](backend/src/main/java/com/ffdash/league/badge/BadgeEligibility.java)) —
not in `LeagueService`, where it originally lived until the eligibility switch (13 cases and
growing) and its helper methods outgrew that class's own orchestration concerns.
`LeagueService.computeBadges` builds one `BadgeContext` per `OwnerSeasonEntry` (bundling the
entry, whether that season is complete, and the handful of extra fields only needed by
lifetime-participation badges like `TOTAL_DEGENERATE`) and asks `BadgeEligibility` whether each
`BadgeType` applies. Internally, `BadgeEligibility` is a `Map<BadgeType, BadgeEvaluator>` built
once from a series of `evaluators.put(BadgeType.X, ctx -> ...)` lines — **adding a new badge
means adding one more `put` line, not a new `switch` case.** `BadgeType`/`BadgeScope`/
`EarnedBadge`/`BadgeEarning` moved into this package too, as the self-contained group they
already were.

**Error handling**: unhandled exceptions go through
[ApiExceptionHandler](backend/src/main/java/com/ffdash/config/ApiExceptionHandler.java)
(`@RestControllerAdvice`), which gives a Sleeper-call failure (`RestClientException` — what
`SleeperClient`'s `RestClient` throws for both HTTP-error and I/O-level failures) a 502 and a
WARN log, and anything else a 500 and an ERROR log — the log-level split is the point: it makes
"an external call failed" visibly distinct from "our own code is broken" in production logs,
where before both looked identical. `UnknownLeagueException` is handled explicitly there too
(even though it has its own `@ResponseStatus(NOT_FOUND)`) — once any `@RestControllerAdvice`
declares an `@ExceptionHandler(Exception.class)` catch-all, Spring resolves that before ever
falling through to `@ResponseStatus`, so a more-specific handler is required, not optional.
**Anywhere you catch a Sleeper-call failure to degrade gracefully** (see
`LeagueService.fetchSeason`'s per-season skip, or `SeasonDataService`/`BracketAssembler`'s
per-bracket/per-week skips), catch `RestClientException` specifically, not a bare
`RuntimeException` — the latter would also silently swallow a genuine bug in the joining logic
and log it identically to an ordinary Sleeper outage.

**Caching**: `SeasonDataService` holds a plain `ConcurrentHashMap<leagueId, CachedEntry>`.
A season with `status == "complete"` is immutable, so once fetched it's cached forever;
an in-progress season is refetched once its entry is older than
`ffdash.cache.live-season-ttl` (default 2m, in `application.yml`). No external cache/DB —
Render's single free-tier instance makes an in-process map sufficient. It's lost on every
cold start (free tier spins down after 15m idle), which is an accepted tradeoff, not a bug.

**Cold-start wake-up (~1 min) is Render's container orchestration, not app startup** —
measured: this app's own JVM+Spring Boot startup is ~1s either way, `spring.main.lazy-
initialization: true` (application.yml) only saved ~0.1s of that. Don't spend more effort
tuning app-side startup time for this; it's not where the delay is. The frontend is what
actually needed to change: every backend fetch goes through
[useApiData](frontend/src/hooks/useApiData.ts) + [LoadingStatus](frontend/src/components/LoadingStatus.tsx),
which auto-retries on failure, flags `slow` once a request has been pending/retrying longer
than a normal response should take (so "waking up the server…" replaces what used to be a
blank screen), and recovers on its own with no page reload needed once the backend responds.
Any new page that fetches from the backend should use this pair rather than rolling its own
loading/error state.

**Leagues are config, not code — including cross-year identity.** Declared in
[application.yml](backend/src/main/resources/application.yml) under `ffdash.leagues`, bound
via [LeaguesProperties](backend/src/main/java/com/ffdash/config/LeaguesProperties.java): each
entry is a `LeagueFamilyConfig(key, displayName, type, seasons)`, where `seasons` is an
ordered list of `{season, leagueId}` — Sleeper's `previous_league_id` season-chaining isn't
used; season ids are hand-supplied. `type` is `FANTASY` or `PICKEM` — Pick'em is a
confidence pool, not a head-to-head roster league, so it's structurally different (see
below). `LeagueService` rejects any `key` not in that list (`UnknownLeagueException` -> 404).
Adding a season to an existing league, or a whole new league, is a YAML edit, no code change.

**`OwnerCareerSummary`'s combined fields are type-aware, not a blind sum**: `combinedWins`/
`combinedLosses`/etc. only include `FANTASY`-type seasons (mixing pick'em outcomes into
head-to-head win/loss totals would misrepresent both). `topThreeFinishes` counts `rank <= 3`
across **all** types including `PICKEM`, but only where `status == "complete"` (a final
placement, not a mid-season snapshot) — a genuinely different, format-agnostic achievement.
If you're adding a new aggregate, check which of these two inclusion rules it should follow.

**`TeamSummary` carries two different names on purpose**: `teamName` is a per-season nickname
(can change every year, even per-league for the same person) used in League View standings;
`ownerDisplayName` is the owner's stable Sleeper username, used to identify the *person*
anywhere identity needs to be consistent across seasons/leagues (Manager View, and
`OwnerCareerSummary.displayName`, which is sourced from it). Don't use `teamName` for the
latter purpose.

**Frontend is routed with `react-router-dom`**, URL-driven rather than component-state-driven
— e.g. the League View season selector is a `?season=` query param (`all` is a valid value,
handled client-side, see below), not local state, so a specific year/aggregate is a
shareable link. Two top-level sections under [App.tsx](frontend/src/App.tsx)'s `TopNav`:
- **League View** (`/leagues/:key`) — [LeaguesPage](frontend/src/pages/LeaguesPage.tsx)
  redirects bare `/leagues` to the first configured family, then renders `LeagueNav` (tabs)
  + [LeagueView](frontend/src/components/LeagueView.tsx). `LeagueView` is remounted (`key={key}`
  in `LeaguesPage`) on every league switch rather than resetting its own state in an effect —
  keep that pattern for similar fetch-on-prop-change components; manually calling `setState`
  synchronously inside an effect is exactly what `oxlint`'s `set-state-in-effect` rule flags.
  Its season `<select>` includes every real season plus `All`; `All` doesn't hit the backend —
  it runs [aggregateAllSeasons](frontend/src/api/aggregations.ts) client-side over the
  already-fetched `LeagueFamilyHistory` (that endpoint returns every season in one call
  anyway), mirroring the backend's own combining logic but scoped to just that one family.
- **Manager View** (`/managers`, `/managers/:userId`) — [ManagerProfilePage](frontend/src/pages/ManagerProfilePage.tsx)
  re-fetches `GET /api/owners` and filters by `userId` client-side (rather than passing data via
  router state) so a direct link/reload works standalone, then composes several cards, two of
  them (badges, the Rivalry Tracker below) substantial enough to be their own components
  ([BadgeGrid](frontend/src/components/BadgeGrid.tsx),
  [RivalryTracker](frontend/src/components/RivalryTracker.tsx)) rather than living inline on
  the page.

**A dropdown driven by both a computed default and a user override** — `WeeklySchedule`'s week
picker, `RivalryTracker`'s opponent picker — uses `useState<T | null>(null)` for the override
plus a value re-derived every render from current props/data, **never a `useEffect` that
syncs/resets it**: if the manual pick is no longer valid for the current props (season/manager
switched under it), the derivation falls back on its own, with no reset code needed. This is
the same "derive, don't sync" principle as `LeagueView`'s `key={key}` remount above, just
applied at the single-value-of-state level instead of whole-component level — reach for it
whenever a `<select>` needs "pick one, but default intelligently."

## Why no database

Two things that look like they need one, don't: `user_id` is stable across leagues for the
same Sleeper account (enables cross-league aggregation by grouping), and Sleeper exposes
season-history natively via `previous_league_id` chaining, even though this app opts to
hand-configure season ids instead of walking that chain live. Both needs turned out to be
config + in-memory caching, not persistence. If a future feature genuinely needs durable
storage (survives restarts, or data with no Sleeper source of truth), don't reach for
Render's own free Postgres — it expires after 30 days unless upgraded. Neon or Supabase's
free tiers don't expire and plug in the same way (a connection-string env var).

**Dev vs. prod API wiring differs and both ends must stay in sync:**
- Dev: [vite.config.ts](frontend/vite.config.ts) proxies `/api/*` to `localhost:8080`;
  [api/leagues.ts](frontend/src/api/leagues.ts) calls relative paths (`VITE_API_BASE_URL` unset).
- Prod: frontend and backend are deployed as separate origins (see Deployment below), so
  `VITE_API_BASE_URL` is baked in at build time and prepended to every API call, and the
  backend's CORS config ([WebConfig](backend/src/main/java/com/ffdash/config/WebConfig.java),
  reading `ffdash.cors-allowed-origins`) must list the frontend's deployed origin via the
  `FFDASH_CORS_ALLOWED_ORIGINS` env var, or every request gets CORS-blocked.
- Backend also reads its listen port from `$PORT` (`server.port: ${PORT:8080}` in
  `application.yml`) since PaaS hosts assign it dynamically — don't hardcode 8080 in code.

**Sleeper API quirks encoded in the DTOs:**
- [SleeperRoster](backend/src/main/java/com/ffdash/sleeper/SleeperRoster.java) — Sleeper
  splits points into whole/decimal integer pairs (`fpts`/`fpts_decimal`); `pointsFor()`/
  `pointsAgainst()` combine them into a single double.
- [SleeperUser](backend/src/main/java/com/ffdash/sleeper/SleeperUser.java) — a team's
  custom name lives in a free-form `metadata` map (`team_name`), not a top-level field;
  `teamName()` falls back to `display_name` when unset.
- The `pickem` family (`Pick Six(teen)`) is a Sleeper Pick'em pool (`sport: "pickem:nfl"`),
  not a real roster league — no team rosters/nicknames, just usernames. This is exactly why
  `LeagueFamilyConfig.type` exists; see the `OwnerCareerSummary` note above for how that
  type is actually used downstream.

## Deployment

Deploys to Render's free tier via the [render.yaml](render.yaml) Blueprint: backend as a
Docker web service (`backend/Dockerfile`, multi-stage — note `build.gradle` disables the
plain `jar` task so the Dockerfile's `*.jar` copy glob stays unambiguous), frontend as a
static site. The two services' env vars (`VITE_API_BASE_URL` on the frontend,
`FFDASH_CORS_ALLOWED_ORIGINS` on the backend) must point at each other's actual deployed
URLs — see comments in `render.yaml` if renaming either service. Full steps in
[README.md](README.md).
