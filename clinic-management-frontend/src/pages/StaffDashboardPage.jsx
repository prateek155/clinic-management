import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, CheckCircle2, Clock3, RefreshCw, Users } from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

export default function StaffDashboardPage() {
  const { session } = useAuth();
  const clinics = useMemo(() => session?.memberships?.filter((item) => item.role === 'STAFF') || [], [session]);
  const [clinicId, setClinicId] = useState(clinics[0]?.clinicId || '');
  const [staff, setStaff] = useState(null);
  const [stats, setStats] = useState(null);
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => { if (clinics[0] && !clinicId) setClinicId(clinics[0].clinicId); }, [clinics, clinicId]);
  useEffect(() => {
    if (!clinicId) return undefined;
    let current = true; setLoading(true); setError('');
    const day = localDate();
    Promise.all([api.get('/staff/me', { params: { clinicId } }), api.get('/staff/dashboard', { params: { clinicId } })])
      .then(async ([staffResponse, statsResponse]) => {
        if (!current) return;
        setStaff(staffResponse.data); setStats(statsResponse.data);
        if (staffResponse.data.permissions.some((permission) => ['APPOINTMENT_READ', 'QUEUE_MANAGE'].includes(permission))) {
          const { data } = await api.get('/staff/appointments', { params: { clinicId, date: day } });
          if (current) setAppointments(data);
        } else if (current) setAppointments([]);
      })
      .catch((cause) => { if (current) setError(errorMessage(cause)); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [clinicId, refresh]);
  async function checkIn(appointment) {
    setError(''); setNotice('');
    try { await api.patch(`/staff/appointments/${appointment.id}/check-in`, null, { params: { clinicId } }); setNotice(`${appointment.patientName} checked in.`); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
  }
  const canQueue = staff?.permissions?.includes('QUEUE_MANAGE');
  return <RoleLayout role="STAFF">
    <div className="page-heading"><div><p className="eyebrow">STAFF WORKSPACE</p><h1>Clinic desk</h1><p>Today’s appointments and waiting queue for your clinic.</p></div><button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button></div>
    {clinics.length > 1 && <label className="filter-label">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{clinics.map((item) => <option key={item.clinicId} value={item.clinicId}>{item.clinicName}</option>)}</select></label>}
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error' : 'inline-alert inline-alert-success'} role="status">{error || notice}</div>}
    {loading ? <section className="workspace-panel"><p className="empty-state">Loading clinic desk…</p></section> : stats && <>
      <section className="staff-metrics"><Metric icon={<CalendarDays size={18} />} label="Today's appointments" value={stats.todayAppointments} /><Metric icon={<Clock3 size={18} />} label="Waiting" value={stats.waitingPatients} /><Metric icon={<CheckCircle2 size={18} />} label="Completed today" value={stats.completedToday} /><Metric icon={<Users size={18} />} label="Clinic patients" value={stats.totalPatients} /></section>
      <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-1"><CalendarDays size={18} /></span><div><h2>Today's schedule</h2><p>{appointments.length} appointment{appointments.length === 1 ? '' : 's'} scheduled today.</p></div></div>
        {appointments.length ? <div className="appointment-list">{appointments.map((appointment) => <article className="appointment-card" key={appointment.id}><div className="appointment-card-top"><div><h3>{appointment.patientName}</h3><p>{appointment.patientCode} · {appointment.doctorName}</p></div><span className={'status-pill status-' + appointment.status.toLowerCase()}>{formatStatus(appointment.status)}</span></div><div className="appointment-meta"><span><Clock3 size={15} />{new Date(appointment.startsAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</span><span>{appointment.reason || 'Visit reason not provided'}</span></div>{canQueue && appointment.status === 'CONFIRMED' && <div className="appointment-actions"><button className="button-primary" onClick={() => checkIn(appointment)}>Mark arrived</button></div>}</article>)}</div> : <p className="empty-state">No appointments are scheduled for today.</p>}
      </section>
      {!staff?.permissions?.length && <p className="staff-permission-note">Your clinic doctor has not assigned any staff permissions yet.</p>}
    </>}
  </RoleLayout>;
}
function Metric({ icon, label, value }) { return <article className="metric-card"><div className="metric-top"><span className="metric-icon metric-icon-0">{icon}</span></div><h2>{label}</h2><div className="metric-value">{value ?? '—'}</div></article>; }
function localDate() { const date = new Date(); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 10); }
function formatStatus(value) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase()); }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.message || error.message || 'Request failed.'; }
