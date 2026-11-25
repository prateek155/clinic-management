import { useEffect, useState } from 'react';
import { CalendarDays, ClipboardList, HeartPulse } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

export default function PatientDashboardPage() {
  const { session } = useAuth();
  const [appointments, setAppointments] = useState([]);
  const [error, setError] = useState('');
  useEffect(() => {
    api.get('/appointments/my')
      .then(({ data }) => setAppointments(data))
      .catch((cause) => setError(errorMessage(cause)));
  }, []);
  const upcoming = appointments.filter((item) => new Date(item.startsAt) > new Date() && !['CANCELLED', 'REJECTED'].includes(item.status));
  const completed = appointments.filter((item) => item.status === 'COMPLETED').length;
  return <RoleLayout role="PATIENT">
    <div className="welcome-row"><div><p className="eyebrow">PATIENT WORKSPACE</p><h1>Your care, <span>{session?.fullName?.split(' ')[0] || 'all in one place'}.</span></h1><p>Book a visit and keep track of your upcoming appointments.</p></div><div className="welcome-art"><div className="art-ring art-ring-one" /><div className="art-ring art-ring-two" /><HeartPulse size={32} strokeWidth={1.5} /></div></div>
    {error && <div className="inline-alert inline-alert-error" role="alert">{error}</div>}
    <section className="dashboard-cards" aria-label="Patient overview">
      <Metric icon={CalendarDays} title="Upcoming appointments" value={upcoming.length} detail="Visits that are still active" />
      <Metric icon={ClipboardList} title="Appointment history" value={appointments.length} detail="All clinics and visit statuses" />
      <Metric icon={HeartPulse} title="Completed visits" value={completed} detail="Completed appointments" />
    </section>
    <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-0"><CalendarDays size={18} /></span><div><h2>Next appointment</h2><p>Your nearest active booking.</p></div><Link className="button-secondary section-action" to="/patient/appointments">Manage appointments</Link></div>
      {upcoming.length ? <article className="appointment-card"><div className="appointment-card-top"><div><h3>{upcoming[0].doctorName}</h3><p>{upcoming[0].clinicName}</p></div><span className={'status-pill status-' + upcoming[0].status.toLowerCase()}>{formatStatus(upcoming[0].status)}</span></div><div className="appointment-meta"><span><CalendarDays size={15} />{new Date(upcoming[0].startsAt).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' })}</span><span>{new Date(upcoming[0].startsAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</span></div></article> : <p className="empty-state">No upcoming appointments. Book a visit when you are ready.</p>}
    </section>
    <div className="dashboard-cta"><div><h2>Need a visit?</h2><p>Browse active clinics and their available doctors.</p></div><Link to="/patient/appointments" className="button-primary">Book an appointment<CalendarDays size={16} /></Link></div>
  </RoleLayout>;
}

function Metric({ icon: Icon, title, value, detail }) {
  return <article className="metric-card"><div className="metric-top"><span className="metric-icon metric-icon-0"><Icon size={18} /></span><span className="metric-link">MY CARE</span></div><h2>{title}</h2><strong className="metric-value">{value}</strong><p>{detail}</p></article>;
}

function formatStatus(value) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase()); }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.detail || error.message || 'Could not load your appointments.'; }
