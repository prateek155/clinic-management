import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, CheckCircle2, Clock3, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

export default function DoctorDashboardPage() {
  const { session } = useAuth();
  const clinics = useMemo(() => session?.memberships?.filter((item) => item.role === 'DOCTOR') || [], [session]);
  const [clinicId, setClinicId] = useState(clinics[0]?.clinicId || '');
  const [stats, setStats] = useState(null);
  const [appointments, setAppointments] = useState([]);
  const [error, setError] = useState('');
  useEffect(() => { if (clinics[0] && !clinicId) setClinicId(clinics[0].clinicId); }, [clinics, clinicId]);
  useEffect(() => {
    if (!clinicId) return undefined;
    let current = true;
    Promise.all([
      api.get('/doctor/dashboard', { params: { clinicId } }),
      api.get('/doctor/appointments', { params: { clinicId, from: localDate(), to: localDate() } }),
    ]).then(([dashboard, list]) => {
      if (current) { setStats(dashboard.data); setAppointments(list.data); setError(''); }
    }).catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, [clinicId]);

  return <RoleLayout role="DOCTOR">
    <div className="page-heading"><div><p className="eyebrow">DOCTOR PORTAL</p><h1>Clinic overview</h1><p>Welcome, Dr. {session?.fullName}. Review today and manage your clinic schedule.</p></div>
      {clinics.length > 1 && <label className="clinic-picker">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{clinics.map((item) => <option key={item.clinicId} value={item.clinicId}>{item.clinicName}</option>)}</select></label>}
    </div>
    {error && <div className="inline-alert inline-alert-error" role="alert">{error}</div>}
    {!clinics.length ? <section className="workspace-panel"><h2>Doctor portal not configured</h2><p>Your Firebase account needs an active doctor membership. Configure the clinic owner bootstrap on the backend, then sign out and sign in again.</p></section> : <>
      <section className="dashboard-cards" aria-label="Doctor overview">
        <Metric icon={Users} title="Patients seen" value={stats?.totalPatients ?? '—'} detail="Patients linked to this clinic" />
        <Metric icon={CalendarDays} title="Today's appointments" value={stats?.todayAppointments ?? '—'} detail="Scheduled for today" />
        <Metric icon={Clock3} title="Pending requests" value={stats?.pendingAppointments ?? '—'} detail="Waiting for a decision" />
      </section>
      <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-1"><CalendarDays size={18} /></span><div><h2>Today's appointments</h2><p>Confirm requests and track the clinic day.</p></div><Link className="button-secondary section-action" to="/doctor/appointments">Open appointments</Link></div>
        {appointments.length ? <div className="appointment-list">{appointments.slice(0, 5).map((item) => <article className="appointment-card" key={item.id}><div className="appointment-card-top"><div><h3>{item.patientName}</h3><p>{item.patientCode} · {item.doctorName}</p></div><span className={'status-pill status-' + item.status.toLowerCase()}>{formatStatus(item.status)}</span></div><div className="appointment-meta"><span><Clock3 size={15} />{new Date(item.startsAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</span>{item.reason && <span>{item.reason}</span>}</div></article>)}</div> : <p className="empty-state">No appointments scheduled for today.</p>}
      </section>
      <div className="dashboard-cta"><div><h2>Set your availability</h2><p>Publish weekly hours so patients can choose open appointment times.</p></div><Link to="/doctor/schedule" className="button-primary">Manage availability<CheckCircle2 size={16} /></Link></div>
    </>}
  </RoleLayout>;
}

function Metric({ icon: Icon, title, value, detail }) {
  return <article className="metric-card"><div className="metric-top"><span className="metric-icon metric-icon-0"><Icon size={18} /></span><span className="metric-link">OVERVIEW</span></div><h2>{title}</h2><strong className="metric-value">{value}</strong><p>{detail}</p></article>;
}
function localDate() { const date = new Date(); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 10); }
function formatStatus(value) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase()); }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.detail || error.message || 'Could not load the doctor dashboard.'; }
