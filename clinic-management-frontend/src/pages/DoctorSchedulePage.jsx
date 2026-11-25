import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

const weekdays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

export default function DoctorSchedulePage() {
  const { session } = useAuth();
  const clinics = useMemo(() => session?.memberships?.filter((item) => item.role === 'DOCTOR') || [], [session]);
  const [clinicId, setClinicId] = useState(clinics[0]?.clinicId || '');
  const [schedules, setSchedules] = useState([]);
  const [exceptions, setExceptions] = useState([]);
  const [exceptionDate, setExceptionDate] = useState(localDate());
  const [exceptionReason, setExceptionReason] = useState('');
  const [weekday, setWeekday] = useState('1');
  const [startsAt, setStartsAt] = useState('09:00');
  const [endsAt, setEndsAt] = useState('17:00');
  const [duration, setDuration] = useState('20');
  const [effectiveFrom, setEffectiveFrom] = useState(localDate());
  const [effectiveUntil, setEffectiveUntil] = useState('');
  const [breaks, setBreaks] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => { if (clinics[0] && !clinicId) setClinicId(clinics[0].clinicId); }, [clinics, clinicId]);
  useEffect(() => {
    if (!clinicId) return undefined;
    let current = true;
    Promise.all([api.get('/doctor/schedules', { params: { clinicId } }), api.get('/doctor/schedule-exceptions', { params: { clinicId } })])
      .then(([scheduleResponse, exceptionResponse]) => { if (current) { setSchedules(scheduleResponse.data); setExceptions(exceptionResponse.data); } })
      .catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, [clinicId, refresh]);

  async function createSchedule(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    const validBreaks = breaks.filter((item) => item.startsAt && item.endsAt).map((item) => ({
      startsAt: item.startsAt, endsAt: item.endsAt, label: item.label || null,
    }));
    try {
      await api.post('/doctor/schedules', {
        clinicId, weekday: Number(weekday), startsAt, endsAt,
        slotDurationMinutes: Number(duration), effectiveFrom,
        effectiveUntil: effectiveUntil || null, breaks: validBreaks,
      });
      setNotice('Weekly availability published. Patients can now book matching open times.');
      setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function deactivate(schedule) {
    if (!window.confirm('Stop offering future times from this weekly schedule? Existing appointments will remain.')) return;
    setError(''); setNotice('');
    try {
      await api.delete('/doctor/schedules/' + schedule.id, { params: { clinicId } });
      setNotice('Schedule deactivated. Existing bookings were kept.');
      setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
  }

  async function addException(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      await api.post('/doctor/schedule-exceptions', { clinicId, exceptionDate, reason: exceptionReason || null });
      setNotice('Clinic closed for this doctor on that date. Existing appointments remain unchanged.');
      setExceptionReason(''); setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function removeException(item) {
    if (!window.confirm(`Reopen available times on ${item.exceptionDate}? Existing bookings will remain.`)) return;
    setError(''); setNotice('');
    try {
      await api.delete(`/doctor/schedule-exceptions/${item.id}`, { params: { clinicId } });
      setNotice('Date reopened. Matching future open slots are available again.'); setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
  }

  function addBreak() { setBreaks((items) => [...items, { startsAt: '', endsAt: '', label: 'Break' }]); }
  function updateBreak(index, key, value) { setBreaks((items) => items.map((item, i) => i === index ? { ...item, [key]: value } : item)); }

  return <RoleLayout role="DOCTOR">
    <div className="page-heading"><div><p className="eyebrow">DOCTOR PORTAL</p><h1>Availability</h1><p>Set recurring clinic hours. Open slots are generated when patients view a date.</p></div><button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button></div>
    {clinics.length > 1 && <label className="filter-label">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{clinics.map((item) => <option key={item.clinicId} value={item.clinicId}>{item.clinicName}</option>)}</select></label>}
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error' : 'inline-alert inline-alert-success'} role="status">{error || notice}</div>}
    {!clinics.length ? <section className="workspace-panel"><h2>Doctor portal not configured</h2><p>Complete the one-time doctor owner setup on the backend, then sign in again.</p></section> : <>
      <section className="workspace-panel"><div className="section-title"><span className="metric-icon metric-icon-0"><Plus size={18} /></span><div><h2>Add weekly hours</h2><p>Times use the clinic's configured timezone.</p></div></div>
        <form className="schedule-grid" onSubmit={createSchedule}>
          <label>Weekday<select className="form-input" value={weekday} onChange={(event) => setWeekday(event.target.value)}>{weekdays.map((day, index) => <option key={day} value={index}>{day}</option>)}</select></label>
          <label>Starts at<input className="form-input" type="time" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} required /></label>
          <label>Ends at<input className="form-input" type="time" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} required /></label>
          <label>Visit length<select className="form-input" value={duration} onChange={(event) => setDuration(event.target.value)}>{[10, 15, 20, 30, 45, 60].map((minutes) => <option key={minutes} value={minutes}>{minutes} minutes</option>)}</select></label>
          <label>Starts on<input className="form-input" type="date" min={localDate()} value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} required /></label>
          <label>Ends on <span className="muted-label">(optional)</span><input className="form-input" type="date" min={effectiveFrom} value={effectiveUntil} onChange={(event) => setEffectiveUntil(event.target.value)} /></label>
          {breaks.map((item, index) => <div className="schedule-break" key={index}><label>Break starts<input className="form-input" type="time" value={item.startsAt} onChange={(event) => updateBreak(index, 'startsAt', event.target.value)} /></label><label>Break ends<input className="form-input" type="time" value={item.endsAt} onChange={(event) => updateBreak(index, 'endsAt', event.target.value)} /></label><label>Label<input className="form-input" value={item.label} onChange={(event) => updateBreak(index, 'label', event.target.value)} /></label><button type="button" className="button-secondary button-danger" onClick={() => setBreaks((items) => items.filter((_, i) => i !== index))} aria-label="Remove break"><Trash2 size={15} /></button></div>)}
          <div className="schedule-actions"><button className="button-secondary" type="button" onClick={addBreak}>Add break</button><button className="button-primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Publish availability'}<CalendarDays size={16} /></button></div>
        </form>
      </section>
      <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-1"><CalendarDays size={18} /></span><div><h2>Weekly schedules</h2><p>Deactivate a schedule to stop future unbooked times.</p></div></div>
        {schedules.filter((item) => item.active).length ? <div className="schedule-list">{schedules.filter((item) => item.active).map((item) => <article className="schedule-card" key={item.id}><div><h3>{weekdays[item.weekday]} · {item.startsAt.slice(0, 5)}–{item.endsAt.slice(0, 5)}</h3><p>{item.slotDurationMinutes}-minute visits · from {item.effectiveFrom}{item.effectiveUntil ? ' to ' + item.effectiveUntil : ' onward'}{item.breaks?.length ? ' · ' + item.breaks.length + ' break(s)' : ''}</p></div><button className="button-secondary button-danger" onClick={() => deactivate(item)}><Trash2 size={15} /> Deactivate</button></article>)}</div> : <p className="empty-state">No active weekly schedules yet.</p>}
      </section>
      <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-2"><CalendarDays size={18} /></span><div><h2>One-day closures</h2><p>Block future booking times for leave or clinic closure. Already booked visits are kept.</p></div></div><form className="schedule-exception-form" onSubmit={addException}><label>Date<input className="form-input" type="date" min={localDate()} value={exceptionDate} onChange={(event) => setExceptionDate(event.target.value)} required /></label><label>Reason (optional)<input className="form-input" maxLength={240} value={exceptionReason} onChange={(event) => setExceptionReason(event.target.value)} placeholder="Leave, holiday, clinic closed" /></label><button className="button-primary" disabled={busy}>{busy ? 'Saving…' : 'Block date'}</button></form>{exceptions.length ? <div className="schedule-list mt-4">{exceptions.map((item) => <article className="schedule-card" key={item.id}><div><h3>{new Date(`${item.exceptionDate}T12:00:00`).toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}</h3><p>{item.reason || 'No reason specified'}</p></div><button className="button-secondary" onClick={() => removeException(item)}>Reopen day</button></article>)}</div> : <p className="empty-state">No upcoming closure dates.</p>}</section>
    </>}
  </RoleLayout>;
}

function localDate() { const date = new Date(); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 10); }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.detail || error.message || 'Request failed.'; }
