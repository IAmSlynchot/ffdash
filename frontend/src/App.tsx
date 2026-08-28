import { Navigate, Route, Routes } from 'react-router-dom'
import './App.css'
import TopNav from './components/TopNav'
import LeaguesPage from './pages/LeaguesPage'
import ManagerListPage from './pages/ManagerListPage'
import ManagerProfilePage from './pages/ManagerProfilePage'

function App() {
  return (
    <div className="app">
      <header className="app-header">
        <img src="/ffDash.jpg" alt="ffDash logo" className="app-logo" />
        <div>
          <h1>ffDash</h1>
          <p className="app-subtitle">The Fantasy Football Dashboard</p>
        </div>
      </header>

      <TopNav />

      <Routes>
        <Route path="/" element={<Navigate to="/leagues" replace />} />
        <Route path="/leagues" element={<LeaguesPage />} />
        <Route path="/leagues/:key" element={<LeaguesPage />} />
        <Route path="/managers" element={<ManagerListPage />} />
        <Route path="/managers/:userId" element={<ManagerProfilePage />} />
        <Route path="*" element={<Navigate to="/leagues" replace />} />
      </Routes>
    </div>
  )
}

export default App
