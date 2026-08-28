import type { LeagueRef } from '../api/leagues'

interface LeagueNavProps {
  leagues: LeagueRef[]
  selectedLeagueId: string | null
  onSelect: (leagueId: string) => void
}

export default function LeagueNav({ leagues, selectedLeagueId, onSelect }: LeagueNavProps) {
  return (
    <nav className="league-nav">
      {leagues.map((league) => (
        <button
          key={league.id}
          className={league.id === selectedLeagueId ? 'league-nav-tab active' : 'league-nav-tab'}
          onClick={() => onSelect(league.id)}
        >
          {league.displayName}
        </button>
      ))}
    </nav>
  )
}
