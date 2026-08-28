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
