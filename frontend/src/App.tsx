import { useEffect, useState } from 'react'
import './App.css'
import { fetchLeagues, type LeagueRef } from './api/leagues'
import LeagueNav from './components/LeagueNav'
import LeagueView from './components/LeagueView'

function App() {
  const [leagues, setLeagues] = useState<LeagueRef[]>([])
  const [selectedLeagueId, setSelectedLeagueId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchLeagues()
      .then((data) => {
        setLeagues(data)
        setSelectedLeagueId((current) => current ?? data[0]?.id ?? null)
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)))
  }, [])

  return (
    <div className="app">
      <h1>Fantasy Football Dashboard</h1>

      {error && <p className="status-message error">Failed to load leagues: {error}</p>}

      <LeagueNav leagues={leagues} selectedLeagueId={selectedLeagueId} onSelect={setSelectedLeagueId} />

      {selectedLeagueId && <LeagueView leagueId={selectedLeagueId} />}
    </div>
  )
}

export default App
