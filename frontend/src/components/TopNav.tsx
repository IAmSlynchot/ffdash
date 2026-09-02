import { NavLink } from 'react-router-dom'

export default function TopNav() {
  return (
    <nav className="top-nav">
      <NavLink to="/leagues" className={({ isActive }) => (isActive ? 'top-nav-tab active' : 'top-nav-tab')}>
        Leagues
      </NavLink>
      <NavLink to="/managers" className={({ isActive }) => (isActive ? 'top-nav-tab active' : 'top-nav-tab')}>
        Managers
      </NavLink>
    </nav>
  )
}
