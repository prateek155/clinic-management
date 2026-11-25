import { Activity, Bell, LayoutDashboard, LogOut, Menu, Stethoscope } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

const roleNames = { PATIENT: 'Patient', DOCTOR: 'Doctor', STAFF: 'Staff' };

export default function RoleLayout({ role, children }) {
  const { session, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const name = roleNames[role];
  const initials = (session?.fullName || 'U').split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase();
  const memberships = session?.memberships?.filter((membership) => membership.role === role) || [];
  const workspaceName = memberships.length === 1 ? memberships[0].clinicName : `${name} workspace`;
  return (
    <div className="workspace-shell">
      <aside className={`sidebar ${open ? 'sidebar-open' : ''}`}>
        <div className="sidebar-brand"><span className="brand-mark brand-mark-light"><Stethoscope size={19} /></span><span className="text-sm font-bold tracking-wide text-white">CARE<span className="text-teal-300">FLOW</span></span><button className="sidebar-close" onClick={() => setOpen(false)} aria-label="Close navigation">×</button></div>
        <div className="clinic-switch"><span className="clinic-avatar"><Activity size={16} /></span><span><strong>{workspaceName}</strong><small>{name} workspace</small></span></div>
        <p className="nav-label">WORKSPACE</p>
        <nav className="nav-list"><Link className="nav-link nav-link-active" to={`/${role.toLowerCase()}`} onClick={() => setOpen(false)}><LayoutDashboard size={17} /> Dashboard</Link></nav>
        <div className="sidebar-bottom"><div className="support-card"><span className="support-icon"><Bell size={15} /></span><strong>Your workspace is ready</strong><p>Clinic tools will appear here as your account is configured.</p></div><button className="user-menu" onClick={signOut}><span className="user-avatar">{initials}</span><span className="user-details"><strong>{session?.fullName}</strong><small>{session?.email}</small></span><LogOut size={16} /></button></div>
      </aside>
      {open && <button className="mobile-scrim" onClick={() => setOpen(false)} aria-label="Close navigation overlay" />}
      <div className="workspace-main"><header className="topbar"><button className="menu-toggle" onClick={() => setOpen(!open)} aria-label="Toggle navigation"><Menu size={20} /></button><div className="breadcrumbs"><span>Workspace</span><b>/</b><strong>Dashboard</strong></div><div className="topbar-actions"><span className="secure-pill"><span /> Secure session</span><div className="topbar-user"><span className="user-avatar">{initials}</span><span>{name}</span></div></div></header><main className="dashboard-content">{children}</main></div>
    </div>
  );
}
