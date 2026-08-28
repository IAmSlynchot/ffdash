# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A dashboard for 3 Sleeper fantasy football leagues: a React frontend and a Spring Boot
backend that proxies/aggregates data from the public [Sleeper API](https://docs.sleeper.com).
Monorepo with `backend/` and `frontend/` as independent, separately-run projects.

## Commands

**Backend** (`backend/`, Java 21, Gradle wrapper — no local Gradle install needed):
```
./gradlew bootRun        # run on http://localhost:8080
./gradlew build          # compile + test + package
./gradlew test           # run tests (none exist yet, but this is wired up)
```

**Frontend** (`frontend/`, React + Vite + TypeScript):
```
npm install
npm run dev               # http://localhost:5173, proxies /api/* to localhost:8080
npm run build              # tsc -b && vite build -> frontend/dist
npm run lint                # oxlint
```

There's no single top-level command — backend and frontend are built/run separately.

## Architecture

**Backend is a thin proxy/aggregator, not a data owner.** It has no database. On each
request to `GET /api/leagues/{id}` ([LeagueController](backend/src/main/java/com/ffdash/league/LeagueController.java)),
[LeagueService](backend/src/main/java/com/ffdash/league/LeagueService.java) calls three
Sleeper endpoints via [SleeperClient](backend/src/main/java/com/ffdash/sleeper/SleeperClient.java)
(`/league/{id}`, `/league/{id}/rosters`, `/league/{id}/users`), joins rosters to users by
`owner_id`, and returns a single [LeagueSummary](backend/src/main/java/com/ffdash/league/LeagueSummary.java)
DTO. `GET /api/leagues` just returns the configured league list/names, no Sleeper call.

**The 3 leagues are config, not code.** They're declared in
[application.yml](backend/src/main/resources/application.yml) under `ffdash.leagues`
(id + displayName), bound via [LeaguesProperties](backend/src/main/java/com/ffdash/config/LeaguesProperties.java).
`LeagueService` rejects any league id not in that list (`UnknownLeagueException` -> 404).
Adding/removing/renaming a league is a one-file YAML edit, no code change.

**Frontend has no routing library.** [App.tsx](frontend/src/App.tsx) holds
`selectedLeagueId` in plain React state; [LeagueNav](frontend/src/components/LeagueNav.tsx)
is a tab bar that flips it, [LeagueView](frontend/src/components/LeagueView.tsx) fetches
and renders the standings for whichever id is selected. This was a deliberate simplicity
choice for 3 static tabs — reach for a router only if deep-linking or more views are needed.

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
- Not every configured league is a normal roster league — one of the three
  (`Pick Six(teen)`) is a Sleeper Pick'em pool (`sport: "pickem:nfl"`), which has no real
  team rosters/nicknames, just usernames. The standings view still renders it, just with
  thinner data.

## Deployment

Deploys to Render's free tier via the [render.yaml](render.yaml) Blueprint: backend as a
Docker web service (`backend/Dockerfile`, multi-stage — note `build.gradle` disables the
plain `jar` task so the Dockerfile's `*.jar` copy glob stays unambiguous), frontend as a
static site. The two services' env vars (`VITE_API_BASE_URL` on the frontend,
`FFDASH_CORS_ALLOWED_ORIGINS` on the backend) must point at each other's actual deployed
URLs — see comments in `render.yaml` if renaming either service. Full steps in
[README.md](README.md).
