import { useEffect, useMemo, useState } from 'react';
import { Building2, Check, Pencil, Plus, Save, Trash2 } from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

const emptySettings = {
  clinicName: '', addressLine: '', city: '', region: '', postalCode: '', phone: '', timezone: 'Asia/Kolkata',
  specialization: '', qualification: '', registrationNumber: '', consultationFee: '0',
  consultationDurationMinutes: 20, cancellationHours: 24, bookingWindowDays: 90,
};
const emptyService = { name: '', description: '', fee: '0' };

export default function DoctorSettingsPage() {
  const { session } = useAuth();
  const clinics = useMemo(() => session?.memberships?.filter((item) => item.role === 'DOCTOR') || [], [session]);
  const [clinicId, setClinicId] = useState(clinics[0]?.clinicId || '');
  const [settings, setSettings] = useState(emptySettings);
  const [services, setServices] = useState([]);
  const [serviceForm, setServiceForm] = useState(emptyService);
  const [editingId, setEditingId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => { if (clinics[0] && !clinicId) setClinicId(clinics[0].clinicId); }, [clinics, clinicId]);
  useEffect(() => {
    if (!clinicId) return undefined;
    let current = true;
    Promise.all([
      api.get('/doctor/settings', { params: { clinicId } }),
      api.get('/doctor/settings/services', { params: { clinicId } }),
    ]).then(([profile, catalog]) => {
      if (current) {
        setSettings({ ...emptySettings, ...profile.data, consultationFee: String(profile.data.consultationFee ?? 0) });
        setServices(catalog.data);
      }
    }).catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, [clinicId, refresh]);

  async function saveSettings(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const { data } = await api.put('/doctor/settings', {
        ...settings, consultationFee: Number(settings.consultationFee),
        consultationDurationMinutes: Number(settings.consultationDurationMinutes),
        cancellationHours: Number(settings.cancellationHours), bookingWindowDays: Number(settings.bookingWindowDays),
      }, { params: { clinicId } });
      setSettings({ ...data, consultationFee: String(data.consultationFee ?? 0) });
      setNotice('Clinic and doctor settings saved. Appointment rules now apply to new bookings and patient changes.');
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function saveService(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const body = { ...serviceForm, fee: Number(serviceForm.fee) };
      if (editingId) await api.put(`/doctor/settings/services/${editingId}`, body, { params: { clinicId } });
      else await api.post('/doctor/settings/services', body, { params: { clinicId } });
      setServiceForm(emptyService); setEditingId(''); setNotice('Clinic service saved.'); setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  function editService(service) {
    setEditingId(service.id);
    setServiceForm({ name: service.name, description: service.description || '', fee: String(service.fee) });
  }

  async function deactivateService(service) {
    if (!window.confirm(`Remove ${service.name} from the active clinic service list?`)) return;
    setError(''); setNotice('');
    try { await api.delete(`/doctor/settings/services/${service.id}`, { params: { clinicId } }); setNotice('Service deactivated.'); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
  }

  const update = (key, value) => setSettings((current) => ({ ...current, [key]: value }));
  return <RoleLayout role="DOCTOR">
    <div className="page-heading"><div><p className="eyebrow">DOCTOR PORTAL</p><h1>Clinic settings</h1><p>Manage the clinic profile, doctor details, booking rules, and services.</p></div></div>
    {clinics.length > 1 && <label className="filter-label">Clinic<select className="form-input" value={clinicId} onChange={(event) => { setClinicId(event.target.value); setError(''); }}>
      {clinics.map((item) => <option key={item.clinicId} value={item.clinicId}>{item.clinicName}</option>)}
    </select></label>}
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error' : 'inline-alert inline-alert-success'} role="status">{error || notice}</div>}
    {!clinics.length ? <section className="workspace-panel"><h2>Doctor portal not configured</h2><p>Complete the one-time doctor owner setup on the backend, then sign in again.</p></section> : <>
      <form className="workspace-panel" onSubmit={saveSettings}>
        <div className="section-title"><span className="metric-icon metric-icon-0"><Building2 size={18} /></span><div><h2>Clinic and doctor profile</h2><p>These details appear on the clinic and booking pages.</p></div></div>
        <div className="settings-grid">
          <label>Clinic name<input className="form-input" required maxLength={160} value={settings.clinicName} onChange={(e) => update('clinicName', e.target.value)} /></label>
          <label>Clinic phone<input className="form-input" maxLength={32} value={settings.phone || ''} onChange={(e) => update('phone', e.target.value)} /></label>
          <label className="settings-wide">Address<input className="form-input" maxLength={300} value={settings.addressLine || ''} onChange={(e) => update('addressLine', e.target.value)} /></label>
          <label>City<input className="form-input" maxLength={100} value={settings.city || ''} onChange={(e) => update('city', e.target.value)} /></label>
          <label>State / region<input className="form-input" maxLength={100} value={settings.region || ''} onChange={(e) => update('region', e.target.value)} /></label>
          <label>Postal code<input className="form-input" maxLength={24} value={settings.postalCode || ''} onChange={(e) => update('postalCode', e.target.value)} /></label>
          <label>Timezone<input className="form-input" required maxLength={64} value={settings.timezone} onChange={(e) => update('timezone', e.target.value)} placeholder="Asia/Kolkata" /></label>
          <label>Specialization<input className="form-input" maxLength={120} value={settings.specialization || ''} onChange={(e) => update('specialization', e.target.value)} /></label>
          <label>Qualification<input className="form-input" maxLength={240} value={settings.qualification || ''} onChange={(e) => update('qualification', e.target.value)} /></label>
          <label>Medical registration number<input className="form-input" maxLength={80} value={settings.registrationNumber || ''} onChange={(e) => update('registrationNumber', e.target.value)} /></label>
          <label>Consultation fee (₹)<input className="form-input" type="number" min="0" step="0.01" required value={settings.consultationFee} onChange={(e) => update('consultationFee', e.target.value)} /></label>
          <label>Default visit length<select className="form-input" value={settings.consultationDurationMinutes} onChange={(e) => update('consultationDurationMinutes', e.target.value)}>{[10, 15, 20, 30, 45, 60, 90, 120].map((n) => <option key={n} value={n}>{n} minutes</option>)}</select></label>
          <label>Minimum cancellation notice (hours)<input className="form-input" type="number" min="0" max="720" required value={settings.cancellationHours} onChange={(e) => update('cancellationHours', e.target.value)} /></label>
          <label>Booking window (days)<input className="form-input" type="number" min="1" max="365" required value={settings.bookingWindowDays} onChange={(e) => update('bookingWindowDays', e.target.value)} /></label>
        </div>
        <div className="settings-save"><button className="button-primary" disabled={busy || !clinicId}>{busy ? 'Saving…' : 'Save clinic settings'}<Save size={16} /></button></div>
      </form>
      <section className="workspace-panel mt-5">
        <div className="section-title"><span className="metric-icon metric-icon-1"><Check size={18} /></span><div><h2>Clinic services</h2><p>Maintain the active services and fees available to your billing workflow.</p></div></div>
        <form className="service-editor" onSubmit={saveService}>
          <label>Service name<input className="form-input" required maxLength={160} value={serviceForm.name} onChange={(e) => setServiceForm((v) => ({ ...v, name: e.target.value }))} /></label>
          <label>Description<input className="form-input" maxLength={500} value={serviceForm.description} onChange={(e) => setServiceForm((v) => ({ ...v, description: e.target.value }))} /></label>
          <label>Fee (₹)<input className="form-input" type="number" min="0" step="0.01" required value={serviceForm.fee} onChange={(e) => setServiceForm((v) => ({ ...v, fee: e.target.value }))} /></label>
          <div className="service-editor-actions"><button className="button-primary" disabled={busy}>{editingId ? 'Update service' : 'Add service'}{editingId ? <Save size={15} /> : <Plus size={15} />}</button>{editingId && <button type="button" className="button-secondary" onClick={() => { setEditingId(''); setServiceForm(emptyService); }}>Cancel edit</button>}</div>
        </form>
        {services.length ? <div className="service-list">{services.map((service) => <article className={`schedule-card ${!service.active ? 'service-inactive' : ''}`} key={service.id}><div><h3>{service.name} · ₹{Number(service.fee).toFixed(2)}</h3><p>{service.description || (service.active ? 'No description' : 'Inactive')}</p></div>{service.active && <div className="service-row-actions"><button className="button-secondary" onClick={() => editService(service)}><Pencil size={14} /> Edit</button><button className="button-secondary button-danger" onClick={() => deactivateService(service)}><Trash2 size={14} /> Deactivate</button></div>}</article>)}</div> : <p className="empty-state">No clinic services yet.</p>}
      </section>
    </>}
  </RoleLayout>;
}

function errorMessage(error) { return error.response?.data?.error || error.response?.data?.detail || error.response?.data?.message || error.message || 'Request failed.'; }
