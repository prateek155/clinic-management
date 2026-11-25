import { useEffect, useState } from 'react';
import { CalendarDays, Clock3, Plus, RefreshCw } from 'lucide-react';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

export default function PatientAppointmentsPage() {
  const [clinics, setClinics] = useState([]);
  const [clinicId, setClinicId] = useState('');
  const [doctors, setDoctors] = useState([]);
  const [doctorId, setDoctorId] = useState('');
  const [date, setDate] = useState(localDate());
  const [slots, setSlots] = useState([]);
  const [appointments, setAppointments] = useState([]);
  const [familyMembers, setFamilyMembers] = useState([]);
  const [familyMemberId, setFamilyMemberId] = useState('');
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let current = true;
    api.get('/clinics').then(({ data }) => {
      if (!current) return;
      setClinics(data);
      if (data.length) setClinicId(data[0].id);
    }).catch((cause) => { if (current) setError(message(cause)); });
    api.get('/appointments/my').then(({ data }) => {
      if (current) setAppointments(data);
    }).catch((cause) => { if (current) setError(message(cause)); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [refresh]);

  useEffect(() => {
    setDoctors([]);
    setDoctorId('');
    setSlots([]);
    if (!clinicId) return undefined;
    let current = true;
    api.get('/clinics/' + clinicId + '/doctors')
      .then(({ data }) => { if (current) setDoctors(data); })
      .catch((cause) => { if (current) setError(message(cause)); });
    return () => { current = false; };
  }, [clinicId]);

  useEffect(() => {
    setFamilyMembers([]); setFamilyMemberId('');
    if (!clinicId) return undefined;
    let current = true;
    api.get('/patient/family', { params: { clinicId } })
      .then(({ data }) => { if (current) setFamilyMembers(data.filter((member) => member.authorizedByPatient)); })
      .catch(() => { if (current) setFamilyMembers([]); });
    return () => { current = false; };
  }, [clinicId]);

  useEffect(() => {
    setSlots([]);
    if (!clinicId || !doctorId || !date) return undefined;
    let current = true;
    api.get('/doctors/' + doctorId + '/slots', { params: { clinicId, date } })
      .then(({ data }) => { if (current) setSlots(data); })
      .catch((cause) => { if (current) setError(message(cause)); });
    return () => { current = false; };
  }, [clinicId, doctorId, date, refresh]);

  async function book(event) {
    event.preventDefault();
    const slotId = new FormData(event.currentTarget).get('slotId');
    if (!slotId) { setError('Select an available time first.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      await api.post('/appointments', { clinicId, doctorProfileId: doctorId, slotId, familyMemberId: familyMemberId || null, reason });
      setReason('');
      setNotice('Appointment request booked. The clinic can confirm it from the doctor portal.');
      setRefresh((value) => value + 1);
    } catch (cause) { setError(message(cause)); }
    finally { setBusy(false); }
  }

  async function cancelAppointment(appointment) {
    if (!window.confirm('Cancel the appointment with ' + appointment.doctorName + '?')) return;
    setError(''); setNotice('');
    try {
      await api.patch('/appointments/' + appointment.id + '/cancel', { reason: 'Cancelled by patient' });
      setNotice('Appointment cancelled.');
      setRefresh((value) => value + 1);
    } catch (cause) { setError(message(cause)); }
  }

  async function updateReason(appointmentId, value) {
    setError(''); setNotice('');
    try {
      await api.put('/appointments/' + appointmentId, { reason: value });
      setNotice('Appointment details updated.');
      setRefresh((current) => current + 1);
    } catch (cause) { setError(message(cause)); }
  }

  async function reschedule(appointmentId, slotId) {
    setError(''); setNotice('');
    try {
      await api.put('/appointments/' + appointmentId + '/reschedule', { slotId });
      setNotice('Reschedule request submitted. The appointment is pending clinic confirmation.');
      setRefresh((current) => current + 1);
    } catch (cause) { setError(message(cause)); }
  }

  return <RoleLayout role="PATIENT">
    <div className="page-heading">
      <div><p className="eyebrow">PATIENT PORTAL</p><h1>Appointments</h1><p>Find a clinic, choose an available time, and manage your visits.</p></div>
      <button className="button-secondary" onClick={() => setRefresh((value) => value + 1)} aria-label="Refresh appointments"><RefreshCw size={16} /> Refresh</button>
    </div>
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error' : 'inline-alert inline-alert-success'} role="status">{error || notice}</div>}

    <section className="workspace-panel">
      <div className="section-title"><span className="metric-icon metric-icon-0"><Plus size={18} /></span><div><h2>Book an appointment</h2><p>Choose from a doctor's published weekly availability.</p></div></div>
      <form className="booking-grid" onSubmit={book}>
        <label>Clinic
          <select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)} required>
            <option value="">Select clinic</option>
            {clinics.map((clinic) => <option key={clinic.id} value={clinic.id}>{clinic.name}{clinic.city ? ' · ' + clinic.city : ''}</option>)}
          </select>
        </label>
        <label>Doctor
          <select className="form-input" value={doctorId} onChange={(event) => setDoctorId(event.target.value)} required disabled={!clinicId}>
            <option value="">Select doctor</option>
            {doctors.map((doctor) => <option key={doctor.doctorProfileId} value={doctor.doctorProfileId}>{doctor.fullName}{doctor.specialization ? ' · ' + doctor.specialization : ''}</option>)}
          </select>
        </label>
        <label>Date
          <input className="form-input" type="date" min={localDate()} value={date} onChange={(event) => setDate(event.target.value)} required />
        </label>
        <label>Booking for
          <select className="form-input" value={familyMemberId} onChange={(event) => setFamilyMemberId(event.target.value)}>
            <option value="">Myself</option>
            {familyMembers.map((member) => <option key={member.id} value={member.id}>{member.fullName} · {member.relationship}</option>)}
          </select>
        </label>
        <label>Available time
          <select className="form-input" name="slotId" required disabled={!doctorId || slots.length === 0}>
            <option value="">{slots.length ? 'Select a time' : 'No open times for this date'}</option>
            {slots.map((slot) => <option key={slot.id} value={slot.id}>{new Date(slot.startsAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })} – {new Date(slot.endsAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</option>)}
          </select>
        </label>
        <label className="booking-reason">Visit reason <span className="muted-label">(optional)</span>
          <textarea className="form-input form-textarea" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Briefly describe why you are booking" />
        </label>
        <div className="booking-submit"><button className="button-primary" type="submit" disabled={busy || !slots.length}>{busy ? 'Booking…' : 'Request appointment'}<CalendarDays size={16} /></button></div>
      </form>
      {!clinics.length && !loading && <p className="empty-state">No active clinics are available yet. Ask your clinic administrator to finish the doctor setup.</p>}
    </section>

    <section className="workspace-panel mt-5">
      <div className="section-title"><span className="metric-icon metric-icon-1"><Clock3 size={18} /></span><div><h2>Your appointments</h2><p>Review details, update the reason, reschedule, or cancel future visits.</p></div></div>
      {loading ? <p className="empty-state">Loading appointments…</p> : appointments.length
        ? <div className="appointment-list">{appointments.map((appointment) => <PatientAppointmentCard key={appointment.id} appointment={appointment} onCancel={cancelAppointment} onUpdate={updateReason} onReschedule={reschedule} />)}</div>
        : <p className="empty-state">No appointments yet. Use the booking form above to request your first visit.</p>}
    </section>
  </RoleLayout>;
}

function PatientAppointmentCard({ appointment, onCancel, onUpdate, onReschedule }) {
  const canChange = ['PENDING', 'CONFIRMED'].includes(appointment.status) && new Date(appointment.startsAt) > new Date();
  const [editing, setEditing] = useState(false);
  const [reason, setReason] = useState(appointment.reason || '');
  useEffect(() => setReason(appointment.reason || ''), [appointment.reason]);
  return <article className="appointment-card">
    <div className="appointment-card-top"><div><h3>{appointment.doctorName}</h3><p>{appointment.clinicName}{appointment.specialization ? ' · ' + appointment.specialization : ''}</p></div><span className={'status-pill status-' + appointment.status.toLowerCase()}>{labelStatus(appointment.status)}</span></div>
    <div className="appointment-meta"><span><CalendarDays size={15} />{new Date(appointment.startsAt).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })}</span><span><Clock3 size={15} />{new Date(appointment.startsAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</span></div>
    <details className="appointment-details"><summary>See appointment details</summary><div className="details-grid"><span>Patient ID</span><strong>{appointment.patientCode}</strong><span>Visit reason</span><strong>{appointment.reason || 'Not provided'}</strong>{appointment.cancellationReason && <><span>Update</span><strong>{appointment.cancellationReason}</strong></>}</div></details>
    {canChange && <div className="appointment-actions">
      <button className="button-secondary" onClick={() => setEditing(!editing)}>{editing ? 'Close edit' : 'Update reason'}</button>
      <button className="button-secondary button-danger" onClick={() => onCancel(appointment)}>Cancel appointment</button>
      <RescheduleForm appointment={appointment} onReschedule={onReschedule} />
    </div>}
    {editing && <form className="inline-edit" onSubmit={(event) => { event.preventDefault(); onUpdate(appointment.id, reason); setEditing(false); }}><label>Visit reason<input className="form-input" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label><button className="button-primary" type="submit">Save changes</button></form>}
  </article>;
}

function RescheduleForm({ appointment, onReschedule }) {
  const [open, setOpen] = useState(false);
  const [date, setDate] = useState('');
  const [slots, setSlots] = useState([]);
  const [slotId, setSlotId] = useState('');
  const [error, setError] = useState('');
  async function loadSlots() {
    setError('');
    try {
      const { data } = await api.get('/doctors/' + appointment.doctorProfileId + '/slots', {
        params: { clinicId: appointment.clinicId, date },
      });
      setSlots(data); setSlotId('');
      if (!data.length) setError('No available times on that date.');
    } catch (cause) { setError(message(cause)); }
  }
  return <div className="reschedule-box">
    <button className="button-secondary" type="button" onClick={() => setOpen(!open)}>{open ? 'Close reschedule' : 'Reschedule'}</button>
    {open && <div className="reschedule-form"><label>New date<input className="form-input" type="date" min={localDate()} value={date} onChange={(event) => setDate(event.target.value)} /></label><button className="button-secondary" type="button" disabled={!date} onClick={loadSlots}>Find times</button>
      {slots.length > 0 && <><select className="form-input" value={slotId} onChange={(event) => setSlotId(event.target.value)}><option value="">Select time</option>{slots.map((slot) => <option key={slot.id} value={slot.id}>{new Date(slot.startsAt).toLocaleString([], { weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })}</option>)}</select><button className="button-primary" type="button" disabled={!slotId} onClick={() => { onReschedule(appointment.id, slotId); setOpen(false); }}>Request new time</button></>}
      {error && <p className="form-hint form-hint-error">{error}</p>}
    </div>}
  </div>;
}

function localDate() {
  const date = new Date();
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 10);
}

function labelStatus(value) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase()); }
function message(error) { return error.response?.data?.error || error.response?.data?.detail || error.response?.data?.message || error.message || 'Request failed.'; }
