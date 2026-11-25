import { Activity, Bell, CalendarDays, ClipboardList, LayoutDashboard, LogOut, Menu, Stethoscope } from 'lucide-react';
import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

const roleNames = { PATIENT: 'Patient', DOCTOR: 'Doctor', STAFF: 'Staff' };
const roleLinks = {
  PATIENT: [['Dashboard', '/patient'], ['Appointments', '/patient/appointments'], ['Medical records', '/patient/records'], ['Billing', '/patient/billing'], ['Profile & family', '/patient/profile']],
  DOCTOR: [['Dashboard', '/doctor'], ['Appointments', '/doctor/appointments'], ['Patients', '/doctor/patients'], ['Availability', '/doctor/schedule'], ['Billing', '/doctor/billing'], ['Staff', '/doctor/staff'], ['Clinic settings', '/doctor/settings']],
  STAFF: [['Dashboard', '/staff'], ['Queue', '/staff/queue'], ['Billing', '/staff/billing']],
};

export default function RoleLayout({ role, children }) {
  const { session, signOut } = useAuth();
  const location = useLocation();
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
        <nav className="nav-list">{roleLinks[role].map(([label, path], index) => <Link key={path} className={`nav-link ${location.pathname === path ? 'nav-link-active' : ''}`} to={path} onClick={() => setOpen(false)}>{index === 0 ? <LayoutDashboard size={17} /> : label === 'Availability' || label === 'Appointments' ? <CalendarDays size={17} /> : <ClipboardList size={17} />}{label}</Link>)}</nav>
        <div className="sidebar-bottom"><div className="support-card"><span className="support-icon"><Bell size={15} /></span><strong>Your workspace is ready</strong><p>Clinic tools will appear here as your account is configured.</p></div><button className="user-menu" onClick={signOut}><span className="user-avatar">{initials}</span><span className="user-details"><strong>{session?.fullName}</strong><small>{session?.email}</small></span><LogOut size={16} /></button></div>
      </aside>
      {open && <button className="mobile-scrim" onClick={() => setOpen(false)} aria-label="Close navigation overlay" />}
      <div className="workspace-main"><header className="topbar"><button className="menu-toggle" onClick={() => setOpen(!open)} aria-label="Toggle navigation"><Menu size={20} /></button><div className="breadcrumbs"><span>Workspace</span><b>/</b><strong>{location.pathname.split('/').filter(Boolean).at(-1) || 'Dashboard'}</strong></div><div className="topbar-actions"><Link className="notification-link" to="/notifications" aria-label="Notifications"><Bell size={18} /></Link><span className="secure-pill"><span /> Secure session</span><div className="topbar-user"><span className="user-avatar">{initials}</span><span>{name}</span></div></div></header><main className="dashboard-content">{children}</main></div>
    </div>
  );
}
