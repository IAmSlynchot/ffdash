// Types mirror the backend's LeagueRef / LeagueSummary / TeamSummary DTOs
// (see backend/src/main/java/com/ffdash/league).

export interface LeagueRef {
  id: string
  displayName: string
}

export interface TeamSummary {
  teamName: string
  avatarUrl: string | null
  wins: number
  losses: number
  ties: number
  pointsFor: number
  pointsAgainst: number
}

export interface LeagueSummary {
  id: string
  name: string
  season: string
  status: string
  totalRosters: number
  teams: TeamSummary[]
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`Request to ${url} failed: ${response.status} ${response.statusText}`)
  }
  return (await response.json()) as T
}

export function fetchLeagues(): Promise<LeagueRef[]> {
  return fetchJson('/api/leagues')
}

export function fetchLeagueSummary(leagueId: string): Promise<LeagueSummary> {
  return fetchJson(`/api/leagues/${leagueId}`)
}
