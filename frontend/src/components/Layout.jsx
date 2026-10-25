import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

const IconMembers = () => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="9" cy="8" r="3.2" /><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
    <circle cx="17" cy="9" r="2.4" /><path d="M16.5 14.2c2.7 0 4.5 2 4.5 4.8" />
  </svg>
);
const IconSheet = () => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="3.5" y="4" width="17" height="16" rx="2.5" /><path d="M3.5 10h17M9.5 10v10" />
  </svg>
);

const IconCollect = () => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="3" y="6" width="18" height="12" rx="2.5" /><circle cx="12" cy="12" r="2.6" /><path d="M6.5 9.5v.01M17.5 14.5v.01" />
  </svg>
);

const NAV = [
  { to: '/members', label: 'Members', icon: <IconMembers /> },
  { to: '/collection', label: 'Collection', icon: <IconCollect /> },
  { to: '/sheet', label: 'Monthly Sheet', icon: <IconSheet /> },
];

export default function Layout() {
  const { user, signOut } = useAuth();
  const displayName = user.fullName || user.username;

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">Sudar Chits</div>
        <nav className="side-nav" aria-label="Main">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} className={({ isActive }) => 'side-link' + (isActive ? ' active' : '')}>
              {n.icon}<span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="content-wrap">
        <header className="topbar">
          <div className="brand brand-mobile">Sudar Chits</div>
          <div className="topbar-right">
            <div className="who">
              <span className="who-name">{displayName}</span>
              <span className="pill pill-soft">{user.role}</span>
            </div>
            <button className="btn btn-ghost" onClick={signOut}>Sign out</button>
          </div>
        </header>
        <main className="main">
          <Outlet />
        </main>
      </div>

      <nav className="bottomnav" aria-label="Main">
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} className={({ isActive }) => 'bottom-link' + (isActive ? ' active' : '')}>
            {n.icon}<span>{n.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
