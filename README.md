# ffdash — Fantasy Football Dashboard

A small dashboard for Sleeper fantasy football leagues. React frontend, Spring Boot
backend that proxies/aggregates data from the public [Sleeper API](https://docs.sleeper.com).

## Structure

- `backend/` — Spring Boot (Gradle) app. Fetches league/roster/user data from Sleeper
  and exposes it as JSON at `/api/leagues`, `/api/leagues/{key}`, and `/api/owners`.
- `frontend/` — React + Vite app. Lets you toggle between leagues and shows each one's
  current-season standings.

## Configuring leagues

Sleeper gives each season of a league its own id, so a league here is a **family**: a
stable `key` (used in the URL/nav) covering one Sleeper league id per season. Configured
in [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)
under `ffdash.leagues`:

```yaml
ffdash:
  leagues:
    - key: depot
      displayName: "The Depot League"
      type: FANTASY   # or PICKEM, for a Sleeper confidence-pool league
      seasons:
        - season: "2026"
          leagueId: "1384614830836563968"
        - season: "2025"
          leagueId: "1253723165759123456"
```

Add a season by adding an entry to `seasons` (find the league id in its Sleeper URL,
`sleeper.com/leagues/<id>/...`); add a whole new league by adding another family. No code
change needed either way.

## Running locally

Requires Java 21+ (bundled Gradle wrapper handles the rest) and Node.js.

**Backend** (starts on http://localhost:8080):

```
cd backend
./gradlew bootRun
```

**Frontend** (starts on http://localhost:5173, proxies `/api` calls to the backend):

```
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and use the tabs at the top to switch between leagues.

## Deploying (Render, free tier)

This repo includes a [render.yaml](render.yaml) Blueprint that deploys both services:

- **Backend** — Docker web service, built from [backend/Dockerfile](backend/Dockerfile).
- **Frontend** — static site, built with `npm run build`.

**Steps:**

1. Push this repo to GitHub.
2. In the [Render dashboard](https://dashboard.render.com), click **New → Blueprint** and connect the repo.
3. Render reads `render.yaml` and proposes both services — review and click **Apply**.
4. First deploy takes a few minutes (backend builds a Docker image; frontend runs `npm install && npm run build`).

You'll end up with:
- `https://ffdash-backend.onrender.com` — the API
- `https://ffdash.onrender.com` — the dashboard

(Rename either service in the Render dashboard before applying if you want different subdomains — just update the `VITE_API_BASE_URL` and `FFDASH_CORS_ALLOWED_ORIGINS` values in `render.yaml` to match, since those two env vars are what let the two services find each other.)

**Free tier note:** the backend web service spins down after 15 minutes of inactivity and can take up to ~a minute to wake back up on the next request (the frontend shows a "waking up the server" message and recovers on its own — no refresh needed — while this happens). The static frontend has no such delay.

**Keeping the backend warm (recommended):** to avoid that wait almost entirely, set up a free uptime monitor to ping the backend every few minutes so it never sits idle long enough to spin down:
1. Create a free account at [uptimerobot.com](https://uptimerobot.com) (or any similar service, e.g. cron-job.org).
2. Add an HTTP(s) monitor pointed at `https://ffdash-backend.onrender.com/api/leagues` (adjust if you renamed the service) — this endpoint is pure config, no outbound Sleeper calls, so it's a cheap, side-effect-free ping.
3. Set the check interval to 5 minutes (comfortably under Render's 15-minute idle timeout).

No code or redeploy needed — it's just external traffic keeping the service active.

**Manual setup (if you'd rather not use the Blueprint):** create a Docker-based Web Service pointed at `backend/` (Dockerfile at `backend/Dockerfile`) with env var `FFDASH_CORS_ALLOWED_ORIGINS=<your frontend URL>`, and a Static Site pointed at `frontend/` with build command `npm install && npm run build`, publish directory `dist`, and env var `VITE_API_BASE_URL=<your backend URL>`.
