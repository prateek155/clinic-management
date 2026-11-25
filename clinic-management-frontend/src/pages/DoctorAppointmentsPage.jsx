import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, Clock3, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

export default function DoctorAppointmentsPage() {
  const { session } = useAuth();
  const navigate = useNavigate();
  const clinics = useMemo(() => session?.memberships?.filter((item) => item.role === 'DOCTOR') || [], [session]);
  const [clinicId, setClinicId] = useState(clinics[0]?.clinicId || '');
  const [from, setFrom] = useState(localDate());
  const [to, setTo] = useState(localDate());
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => { if (clinics[0] && !clinicId) setClinicId(clinics[0].clinicId); }, [clinics, clinicId]);
  useEffect(() => {
    if (!clinicId) return undefined;
    let current = true;
    setLoading(true); setError('');
    api.get('/doctor/appointments', { params: { clinicId, from, to } })
      .then(({ data }) => { if (current) setAppointments(data); })
      .catch((cause) => { if (current) setError(errorMessage(cause)); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [clinicId, from, to, refresh]);

  async function setStatus(appointment, status) {
    setError(''); setNotice('');
    const reason = ['CANCELLED', 'REJECTED', 'NO_SHOW'].includes(status)
      ? window.prompt('Optional note for the appointment history:') || ''
      : '';
    try {
      await api.patch('/doctor/appointments/' + appointment.id + '/status', { clinicId, status, reason });
      setNotice('Appointment updated to ' + formatStatus(status) + '.');
      setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
  }

  async function reschedule(appointmentId, slotId) {
    setError(''); setNotice('');
    try {
      await api.put('/doctor/appointments/' + appointmentId + '/reschedule', { clinicId, slotId });
      setNotice('New time requested. The appointment is pending clinic confirmation.');
      setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
  }

  return <RoleLayout role="DOCTOR">
    <div className="page-heading"><div><p className="eyebrow">DOCTOR PORTAL</p><h1>Appointments</h1><p>Review booking requests, open patient details, and update visit status.</p></div>
      <button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button>
    </div>
    {clinics.length > 1 && <label className="filter-label">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{clinics.map((item) => <option key={item.clinicId} value={item.clinicId}>{item.clinicName}</option>)}</select></label>}
    <div className="appointment-filters"><label>From<input className="form-input" type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label><label>To<input className="form-input" type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label></div>
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error' : 'inline-alert inline-alert-success'} role="status">{error || notice}</div>}
    <section className="workspace-panel">
      <div className="section-title"><span className="metric-icon metric-icon-1"><CalendarDays size={18} /></span><div><h2>Clinic bookings</h2><p>{appointments.length} appointment{appointments.length === 1 ? '' : 's'} in this date range.</p></div></div>
      {loading ? <p className="empty-state">Loading appointments…</p> : appointments.length
        ? <div className="appointment-list">{appointments.map((appointment) => <DoctorAppointmentCard key={appointment.id} appointment={appointment} onStatus={setStatus} onReschedule={reschedule} onOpenConsultation={() => navigate(`/doctor/appointments/${appointment.id}/consultation?clinicId=${clinicId}`)} />)}</div>
        : <p className="empty-state">No appointments in this date range.</p>}
    </section>
  </RoleLayout>;
}

function DoctorAppointmentCard({ appointment, onStatus, onReschedule, onOpenConsultation }) {
  const [showDetails, setShowDetails] = useState(false);
  const actions = {
    PENDING: [['CONFIRMED', 'Confirm'], ['REJECTED', 'Reject']],
    CONFIRMED: [['CHECKED_IN', 'Mark arrived'], ['NO_SHOW', 'No show'], ['CANCELLED', 'Cancel']],
    CHECKED_IN: [['IN_CONSULTATION', 'Start consultation']],
    IN_CONSULTATION: [['COMPLETED', 'Complete visit']],
  }[appointment.status] || [];
  return <article className="appointment-card">
    <div className="appointment-card-top"><div><h3>{appointment.patientName}</h3><p>{appointment.patientCode} · {appointment.patientEmail}</p></div><span className={'status-pill status-' + appointment.status.toLowerCase()}>{formatStatus(appointment.status)}</span></div>
    <div className="appointment-meta"><span><CalendarDays size={15} />{new Date(appointment.startsAt).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })}</span><span><Clock3 size={15} />{new Date(appointment.startsAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</span><span>{appointment.doctorName}</span></div>
    <div className="appointment-actions">{actions.map(([status, label]) => <button key={status} className={status === 'REJECTED' || status === 'CANCELLED' ? 'button-secondary button-danger' : 'button-secondary'} onClick={() => onStatus(appointment, status)}>{label}</button>)}
      {appointment.status === 'IN_CONSULTATION' && <button className="button-primary" onClick={onOpenConsultation}>Open consultation</button>}
      <button className="button-secondary" onClick={() => setShowDetails(!showDetails)}>{showDetails ? 'Close details' : 'Details'}</button>
    </div>
    {showDetails && <div className="doctor-detail-panel"><div className="details-grid"><span>Clinic</span><strong>{appointment.clinicName}</strong><span>Visit reason</span><strong>{appointment.reason || 'Not provided'}</strong><span>Status</span><strong>{formatStatus(appointment.status)}</strong>{appointment.cancellationReason && <><span>Decision note</span><strong>{appointment.cancellationReason}</strong></>}</div><a className="patient-record-link" href={`/doctor/patients/${appointment.clinicPatientId}?clinicId=${appointment.clinicId}`}>Open full patient record</a>
      {['PENDING', 'CONFIRMED'].includes(appointment.status) && new Date(appointment.startsAt) > new Date() && <DoctorRescheduleForm appointment={appointment} onReschedule={onReschedule} />}
    </div>}
  </article>;
}

function DoctorRescheduleForm({ appointment, onReschedule }) {
  const [date, setDate] = useState('');
  const [slots, setSlots] = useState([]);
  const [slotId, setSlotId] = useState('');
  const [error, setError] = useState('');
  async function findSlots() {
    setError('');
    try {
      const { data } = await api.get('/doctors/' + appointment.doctorProfileId + '/slots', {
        params: { clinicId: appointment.clinicId, date },
      });
      setSlots(data); setSlotId('');
      if (!data.length) setError('No open times for this date.');
    } catch (cause) { setError(errorMessage(cause)); }
  }
  return <div className="reschedule-form doctor-reschedule"><h4>Move to another available time</h4><label>New date<input className="form-input" type="date" min={localDate()} value={date} onChange={(event) => setDate(event.target.value)} /></label><button className="button-secondary" type="button" disabled={!date} onClick={findSlots}>Find times</button>
    {slots.length > 0 && <><select className="form-input" value={slotId} onChange={(event) => setSlotId(event.target.value)}><option value="">Select time</option>{slots.map((slot) => <option key={slot.id} value={slot.id}>{new Date(slot.startsAt).toLocaleString([], { weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })}</option>)}</select><button className="button-primary" type="button" disabled={!slotId} onClick={() => onReschedule(appointment.id, slotId)}>Request new time</button></>}
    {error && <p className="form-hint form-hint-error">{error}</p>}
  </div>;
}

function localDate() { const date = new Date(); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 10); }
function formatStatus(value) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase()); }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.detail || error.message || 'Request failed.'; }
