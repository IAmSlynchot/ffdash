# ffdash — Fantasy Football Dashboard

A small dashboard for 3 Sleeper fantasy football leagues. React frontend, Spring Boot
backend that proxies/aggregates data from the public [Sleeper API](https://docs.sleeper.com).

## Structure

- `backend/` — Spring Boot (Gradle) app. Fetches league/roster/user data from Sleeper
  and exposes it as JSON at `/api/leagues` and `/api/leagues/{id}`.
- `frontend/` — React + Vite app. Lets you toggle between the 3 leagues and shows
  each one's standings.

## Configuring leagues

The 3 leagues are configured in [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)
under `ffdash.leagues`, each with a Sleeper `id` (from the league's URL,
`sleeper.com/leagues/<id>/...`) and a `displayName`. Edit that file to add, remove,
or rename leagues.

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
- `https://ffdash-frontend.onrender.com` — the dashboard

(Rename either service in the Render dashboard before applying if you want different subdomains — just update the `VITE_API_BASE_URL` and `FFDASH_CORS_ALLOWED_ORIGINS` values in `render.yaml` to match, since those two env vars are what let the two services find each other.)

**Free tier note:** the backend web service spins down after 15 minutes of inactivity and takes ~30–50s to wake back up on the next request. The static frontend has no such delay.

**Manual setup (if you'd rather not use the Blueprint):** create a Docker-based Web Service pointed at `backend/` (Dockerfile at `backend/Dockerfile`) with env var `FFDASH_CORS_ALLOWED_ORIGINS=<your frontend URL>`, and a Static Site pointed at `frontend/` with build command `npm install && npm run build`, publish directory `dist`, and env var `VITE_API_BASE_URL=<your backend URL>`.
