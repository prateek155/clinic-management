import { CalendarDays, ClipboardList, Clock3, HeartPulse, Users } from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';

const dashboardCopy = {
  DOCTOR: { greeting: 'Your clinic at a glance', description: 'Your workspace is ready. Add your clinic details and schedule to start managing care.', cards: [['Clinic setup', 'Next step', 'Add your clinic profile'], ['Appointments', 'Ready when you are', 'Scheduling opens in Phase 2'], ['Patient records', 'Protected', 'Available as workflows are enabled']] },
  STAFF: { greeting: 'A smoother clinic day starts here', description: 'Your staff workspace is set up. Your clinic administrator can assign the tools you need.', cards: [['Today’s queue', 'Not configured', 'Ask your clinic administrator'], ['Appointments', 'Not configured', 'Scheduling opens in Phase 2'], ['Patient registration', 'Protected', 'Available as workflows are enabled']] },
  PATIENT: { greeting: 'Your care, all in one place', description: 'Your secure patient account is ready. Connect with a clinic to see appointments and health records here.', cards: [['Appointments', 'No clinic linked', 'Find a clinic in Phase 2'], ['Prescriptions', 'Private and secure', 'Your prescriptions will appear here'], ['Health records', 'Private and secure', 'Your reports will appear here']] },
};
const icons = [CalendarDays, ClipboardList, HeartPulse];

export default function DashboardPage({ role }) {
  const { session } = useAuth();
  const content = dashboardCopy[role];
  return <RoleLayout role={role}>
    <div className="welcome-row"><div><p className="eyebrow">{new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}</p><h1>{content.greeting}, <span>{session?.fullName?.split(' ')[0] || 'there'}.</span></h1><p>{content.description}</p></div><div className="welcome-art"><div className="art-ring art-ring-one" /><div className="art-ring art-ring-two" /><HeartPulse size={32} strokeWidth={1.5} /></div></div>
    <section className="dashboard-cards" aria-label="Workspace overview">{content.cards.map(([label, value, detail], index) => { const Icon = icons[index]; return <article className="metric-card" key={label}><div className="metric-top"><span className={`metric-icon metric-icon-${index}`}><Icon size={18} /></span><span className="metric-link">{role === 'PATIENT' ? 'MY CARE' : 'OVERVIEW'}</span></div><h2>{label}</h2><strong className="metric-value">{value}</strong><p>{detail}</p></article>; })}</section>
    <section className="next-step-panel"><div className="next-icon"><Clock3 size={19} /></div><div><p className="eyebrow">UP NEXT</p><h2>{role === 'DOCTOR' ? 'Set up your clinic profile' : role === 'STAFF' ? 'Wait for your clinic invitation' : 'Connect with a clinic'}</h2><p>{role === 'DOCTOR' ? 'Once your profile is complete, you can define schedules and open appointments.' : role === 'STAFF' ? 'Your doctor or clinic administrator controls your access and permissions.' : 'After joining a clinic, you can book appointments and access your health information.'}</p></div><span className="phase-badge">PHASE 2</span></section>
    <footer className="dashboard-footer"><span><Users size={14} /> Care teams, connected.</span><span>Careflow Clinic Management <b>·</b> Foundation</span></footer>
  </RoleLayout>;
}
