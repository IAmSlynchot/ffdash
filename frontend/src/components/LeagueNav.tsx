import { NavLink } from 'react-router-dom'
import type { LeagueFamilyRef } from '../api/leagues'

interface LeagueNavProps {
  leagues: LeagueFamilyRef[]
}

export default function LeagueNav({ leagues }: LeagueNavProps) {
  return (
    <nav className="league-nav">
      {leagues.map((league) => (
        <NavLink
          key={league.key}
          to={`/leagues/${league.key}`}
          className={({ isActive }) => (isActive ? 'league-nav-tab active' : 'league-nav-tab')}
        >
          {league.displayName}
        </NavLink>
      ))}
    </nav>
  )
}
