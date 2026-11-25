import { useEffect, useMemo, useState } from 'react';
import { Heart, Plus, RefreshCw, Save, ShieldCheck, UserRound, UserX } from 'lucide-react';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import { api } from '../services/api.js';

const blankProfile = { fullName: '', phone: '', dateOfBirth: '', sexAtBirth: '', preferredName: '', emergencyContactName: '', emergencyContactPhone: '' };
const blankFamily = { fullName: '', relationship: '', dateOfBirth: '', phone: '' };

export default function PatientProfilePage() {
  const { refreshSession } = useAuth();
  const [profile, setProfile] = useState(blankProfile);
  const [form, setForm] = useState(blankProfile);
  const [clinics, setClinics] = useState([]);
  const [clinicId, setClinicId] = useState('');
  const [family, setFamily] = useState([]);
  const [familyForm, setFamilyForm] = useState(blankFamily);
  const [editing, setEditing] = useState('');
  const [familyDraft, setFamilyDraft] = useState(blankFamily);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);
  const clinicOptions = useMemo(() => clinics, [clinics]);
  useEffect(() => {
    let current = true;
    Promise.all([api.get('/patient/profile'), api.get('/clinics')])
      .then(([profileResponse, clinicResponse]) => {
        if (!current) return;
        const data = profileResponse.data;
        setProfile(data); setForm({ ...blankProfile, ...data, dateOfBirth: data.dateOfBirth || '' });
        setClinics(clinicResponse.data); if (!clinicId && clinicResponse.data[0]) setClinicId(clinicResponse.data[0].id);
      })
      .catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, []);
  useEffect(() => {
    if (!clinicId) { setFamily([]); return undefined; }
    let current = true;
    api.get('/patient/family', { params: { clinicId } }).then(({ data }) => { if (current) setFamily(data); })
      .catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, [clinicId, refresh]);
  function setProfileField(key, value) { setForm((old) => ({ ...old, [key]: value })); }
  function setFamilyField(key, value) { setFamilyForm((old) => ({ ...old, [key]: value })); }
  async function saveProfile(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try { const { data } = await api.put('/patient/profile', { ...form, dateOfBirth: form.dateOfBirth || null }); setProfile(data); setForm({ ...blankProfile, ...data, dateOfBirth: data.dateOfBirth || '' }); await refreshSession(); setNotice('Profile saved.'); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  async function addFamily(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try { await api.post('/patient/family', { ...familyForm, dateOfBirth: familyForm.dateOfBirth || null }, { params: { clinicId } }); setFamilyForm(blankFamily); setNotice('Family member added and authorized for appointment booking.'); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  async function saveFamily(event, id) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try { await api.put(`/patient/family/${id}`, { ...familyDraft, dateOfBirth: familyDraft.dateOfBirth || null }, { params: { clinicId } }); setEditing(''); setNotice('Family member details saved.'); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  async function revoke(member) {
    if (!window.confirm(`Revoke appointment booking access for ${member.fullName}? Existing records will be kept.`)) return;
    setError(''); setNotice('');
    try { await api.delete(`/patient/family/${member.id}`, { params: { clinicId } }); setNotice('Family booking access revoked.'); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
  }
  return <RoleLayout role="PATIENT">
    <div className="page-heading"><div><p className="eyebrow">PATIENT PORTAL</p><h1>Profile & family</h1><p>Keep your contact details current and manage dependents you can book for.</p></div><button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button></div>
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error' : 'inline-alert inline-alert-success'} role="status">{error || notice}</div>}
    <section className="workspace-panel"><div className="section-title"><span className="metric-icon metric-icon-0"><UserRound size={18} /></span><div><h2>Personal details</h2><p>Used by the clinic team to identify and contact you.</p></div></div><form className="profile-grid" onSubmit={saveProfile}>
      <label>Full name<input className="form-input" value={form.fullName} onChange={(event) => setProfileField('fullName', event.target.value)} required maxLength={120} /></label>
      <label>Email<input className="form-input" value={profile.email || ''} readOnly /></label>
      <label>Phone<input className="form-input" value={form.phone} onChange={(event) => setProfileField('phone', event.target.value)} maxLength={32} /></label>
      <label>Date of birth<input className="form-input" type="date" max={localDate()} value={form.dateOfBirth} onChange={(event) => setProfileField('dateOfBirth', event.target.value)} /></label>
      <label>Sex at birth<select className="form-input" value={form.sexAtBirth} onChange={(event) => setProfileField('sexAtBirth', event.target.value)}><option value="">Prefer not to say</option><option value="FEMALE">Female</option><option value="MALE">Male</option><option value="INTERSEX">Intersex</option><option value="UNKNOWN">Unknown</option><option value="PREFER_NOT_TO_SAY">Prefer not to say</option></select></label>
      <label>Preferred name<input className="form-input" value={form.preferredName} onChange={(event) => setProfileField('preferredName', event.target.value)} maxLength={120} /></label>
      <label>Emergency contact name<input className="form-input" value={form.emergencyContactName} onChange={(event) => setProfileField('emergencyContactName', event.target.value)} maxLength={120} /></label>
      <label>Emergency contact phone<input className="form-input" value={form.emergencyContactPhone} onChange={(event) => setProfileField('emergencyContactPhone', event.target.value)} maxLength={32} /></label>
      <div className="form-actions"><button className="button-primary" disabled={busy}><Save size={16} /> Save profile</button></div>
    </form></section>
    <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-1"><Heart size={18} /></span><div><h2>Family members</h2><p>Booking consent is scoped to the selected clinic. Revoking consent preserves existing appointments and records.</p></div></div>
      {clinicOptions.length > 0 ? <label className="filter-label">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{clinics.map((clinic) => <option key={clinic.id} value={clinic.id}>{clinic.name}</option>)}</select></label> : <p className="empty-state">No active clinics are available yet.</p>}
      {clinicId && <form className="profile-grid family-add-form" onSubmit={addFamily}><label>Full name<input className="form-input" value={familyForm.fullName} onChange={(event) => setFamilyField('fullName', event.target.value)} required maxLength={120} /></label><label>Relationship<input className="form-input" value={familyForm.relationship} onChange={(event) => setFamilyField('relationship', event.target.value)} required maxLength={48} placeholder="e.g. Child" /></label><label>Date of birth<input className="form-input" type="date" max={localDate()} value={familyForm.dateOfBirth} onChange={(event) => setFamilyField('dateOfBirth', event.target.value)} /></label><label>Phone<input className="form-input" value={familyForm.phone} onChange={(event) => setFamilyField('phone', event.target.value)} maxLength={32} /></label><div className="form-actions"><button className="button-secondary" disabled={busy}><Plus size={16} /> Add family member</button></div></form>}
      {family.length ? <div className="family-list">{family.map((member) => <article className="family-card" key={member.id}><div className="family-card-head"><div><h3>{member.fullName}</h3><p>{member.relationship}{member.dateOfBirth ? ' · ' + member.dateOfBirth : ''}{member.phone ? ' · ' + member.phone : ''}</p></div><span className={'status-pill ' + (member.authorizedByPatient ? 'status-confirmed' : 'status-cancelled')}>{member.authorizedByPatient ? 'Booking enabled' : 'Revoked'}</span></div>{editing === member.id && member.authorizedByPatient ? <form className="profile-grid" onSubmit={(event) => saveFamily(event, member.id)}><label>Name<input className="form-input" value={familyDraft.fullName} onChange={(event) => setFamilyDraft((old) => ({ ...old, fullName: event.target.value }))} required /></label><label>Relationship<input className="form-input" value={familyDraft.relationship} onChange={(event) => setFamilyDraft((old) => ({ ...old, relationship: event.target.value }))} required /></label><label>Date of birth<input className="form-input" type="date" max={localDate()} value={familyDraft.dateOfBirth} onChange={(event) => setFamilyDraft((old) => ({ ...old, dateOfBirth: event.target.value }))} /></label><label>Phone<input className="form-input" value={familyDraft.phone} onChange={(event) => setFamilyDraft((old) => ({ ...old, phone: event.target.value }))} /></label><div className="form-actions"><button className="button-primary">Save</button><button className="button-secondary" type="button" onClick={() => setEditing('')}>Cancel</button></div></form> : <div className="appointment-actions">{member.authorizedByPatient && <><button className="button-secondary" onClick={() => { setEditing(member.id); setFamilyDraft({ fullName: member.fullName, relationship: member.relationship, dateOfBirth: member.dateOfBirth || '', phone: member.phone || '' }); }}>Edit details</button><button className="button-secondary button-danger" onClick={() => revoke(member)}><UserX size={15} /> Revoke booking access</button></>}</div>}</article>)}</div> : clinicId && <p className="empty-state">No family members have been added for this clinic.</p>}
    </section>
    <p className="privacy-note"><ShieldCheck size={14} /> A family member can be selected for booking only while authorization is active.</p>
  </RoleLayout>;
}
function localDate() { const date = new Date(); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 10); }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.message || error.message || 'Request failed.'; }
