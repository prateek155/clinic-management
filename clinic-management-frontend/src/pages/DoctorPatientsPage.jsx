import { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Download, HeartPulse, RefreshCw, Search, ShieldAlert, Upload, UserRound } from 'lucide-react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

export default function DoctorPatientsPage() {
  const { clinicPatientId } = useParams();
  const [search] = useSearchParams();
  const { session } = useAuth();
  const clinics = useMemo(() => session?.memberships?.filter((item) => item.role === 'DOCTOR') || [], [session]);
  const [clinicId, setClinicId] = useState(search.get('clinicId') || clinics[0]?.clinicId || '');
  const [query, setQuery] = useState('');
  const [patients, setPatients] = useState([]);
  const [detail, setDetail] = useState(null);
  const [reports, setReports] = useState([]);
  const [reportCategory, setReportCategory] = useState('LAB');
  const [reportDescription, setReportDescription] = useState('');
  const [reportFile, setReportFile] = useState(null);
  const [historyForm, setHistoryForm] = useState({ category: 'Medical history', description: '' });
  const [allergyForm, setAllergyForm] = useState({ allergen: '', reaction: '', severity: 'UNKNOWN', notes: '' });
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (search.get('clinicId')) setClinicId(search.get('clinicId')); else if (clinics[0] && !clinicId) setClinicId(clinics[0].clinicId); }, [clinics, clinicId, search]);
  useEffect(() => {
    if (!clinicId) return undefined;
    let current = true; const timer = setTimeout(() => {
      api.get('/doctor/patients', { params: { clinicId, q: query } })
        .then(({ data }) => { if (current) setPatients(data); })
        .catch((cause) => { if (current) setError(errorMessage(cause)); });
    }, 180);
    return () => { current = false; clearTimeout(timer); };
  }, [clinicId, query, refresh]);
  useEffect(() => {
    if (!clinicPatientId || !clinicId) { setDetail(null); return undefined; }
    let current = true; setError('');
    api.get(`/doctor/patients/${clinicPatientId}`, { params: { clinicId } })
      .then(({ data }) => { if (current) setDetail(data); })
      .catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, [clinicPatientId, clinicId, refresh]);
  useEffect(() => {
    if (!clinicPatientId || !clinicId) { setReports([]); return undefined; }
    let current = true;
    api.get(`/doctor/patients/${clinicPatientId}/reports`, { params: { clinicId } })
      .then(({ data }) => { if (current) setReports(data); })
      .catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, [clinicPatientId, clinicId, refresh]);
  async function addHistory(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try { await api.post(`/doctor/patients/${clinicPatientId}/medical-history`, historyForm, { params: { clinicId } }); setHistoryForm((old) => ({ ...old, description: '' })); setNotice('Medical history entry saved.'); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  async function addAllergy(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try { await api.post(`/doctor/patients/${clinicPatientId}/allergies`, allergyForm, { params: { clinicId } }); setAllergyForm({ allergen: '', reaction: '', severity: 'UNKNOWN', notes: '' }); setNotice('Allergy record saved.'); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  async function uploadReport(event) {
    event.preventDefault(); if (!reportFile) return;
    setBusy(true); setError(''); setNotice('');
    const formData = new FormData(); formData.append('file', reportFile);
    try { await api.post(`/doctor/patients/${clinicPatientId}/reports`, formData, { params: { clinicId, category: reportCategory, description: reportDescription || undefined }, headers: { 'Content-Type': 'multipart/form-data' } }); setReportFile(null); setReportDescription(''); event.currentTarget.reset(); setNotice('Report added to this clinic patient record.'); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  async function downloadReport(report) {
    setError('');
    try { const { data } = await api.get(`/doctor/reports/${report.id}/download`, { params: { clinicId } }); window.open(data.url, '_blank', 'noopener,noreferrer'); }
    catch (cause) { setError(errorMessage(cause)); }
  }
  return <RoleLayout role="DOCTOR">
    <div className="page-heading"><div><p className="eyebrow">DOCTOR PORTAL</p><h1>{detail?.patient?.fullName || 'Patient records'}</h1><p>{detail ? `${detail.patient.patientCode} · ${detail.patient.email}` : 'Search patients registered at your clinic and review their care history.'}</p></div><button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button></div>
    {clinics.length > 1 && <label className="filter-label">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{clinics.map((item) => <option key={item.clinicId} value={item.clinicId}>{item.clinicName}</option>)}</select></label>}
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error' : 'inline-alert inline-alert-success'} role="status">{error || notice}</div>}
    {!clinicPatientId ? <section className="workspace-panel"><div className="section-title"><span className="metric-icon metric-icon-0"><Search size={18} /></span><div><h2>Patient directory</h2><p>Search by name, email, phone, or clinic patient ID.</p></div></div><label className="patient-search"><Search size={16} /><input className="form-input" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search clinic patients" /></label>{patients.length ? <div className="patient-directory">{patients.map((patient) => <Link className="patient-directory-row" key={patient.clinicPatientId} to={`/doctor/patients/${patient.clinicPatientId}?clinicId=${clinicId}`}><span className="clinic-avatar"><UserRound size={16} /></span><span><strong>{patient.fullName}</strong><small>{patient.patientCode} · {patient.email}{patient.phone ? ' · ' + patient.phone : ''}</small></span><span className="patient-last-visit">{patient.appointmentCount} visits{patient.lastAppointmentAt ? ` · last ${new Date(patient.lastAppointmentAt).toLocaleDateString()}` : ''}</span></Link>)}</div> : <p className="empty-state">No clinic patients match this search.</p>}</section> : detail && <>
      <div className="patient-detail-back"><Link to={`/doctor/patients?clinicId=${clinicId}`} className="button-secondary"><ArrowLeft size={15} /> Patient directory</Link></div>
      <div className="patient-overview-grid"><section className="workspace-panel"><div className="section-title"><span className="metric-icon metric-icon-0"><UserRound size={18} /></span><div><h2>Patient profile</h2><p>Clinic record {detail.patient.patientCode}</p></div></div><div className="details-grid"><span>Full name</span><strong>{detail.profile.fullName}</strong><span>Email</span><strong>{detail.profile.email}</strong><span>Phone</span><strong>{detail.profile.phone || 'Not provided'}</strong><span>Date of birth</span><strong>{detail.profile.dateOfBirth || 'Not provided'}</strong><span>Sex at birth</span><strong>{detail.profile.sexAtBirth || 'Not provided'}</strong><span>Emergency contact</span><strong>{detail.profile.emergencyContactName || 'Not provided'}{detail.profile.emergencyContactPhone ? ' · ' + detail.profile.emergencyContactPhone : ''}</strong><span>Visits</span><strong>{detail.patient.appointmentCount}</strong></div></section>
        <section className="workspace-panel"><div className="section-title"><span className="metric-icon metric-icon-2"><ShieldAlert size={18} /></span><div><h2>Allergies</h2><p>Review before prescribing or starting treatment.</p></div></div>{detail.allergies.length ? <div className="record-list">{detail.allergies.map((item) => <article className="allergy-row" key={item.id}><strong>{item.allergen}</strong><span className={'status-pill status-' + (item.severity || 'unknown').toLowerCase()}>{item.severity || 'Unknown'}</span><p>{item.reaction || 'Reaction not provided'}{item.notes ? ' · ' + item.notes : ''}</p></article>)}</div> : <p className="empty-state">No allergies are recorded.</p>}<form className="patient-add-form" onSubmit={addAllergy}><input className="form-input" placeholder="Allergen" required maxLength={160} value={allergyForm.allergen} onChange={(event) => setAllergyForm((old) => ({ ...old, allergen: event.target.value }))} /><input className="form-input" placeholder="Reaction" maxLength={300} value={allergyForm.reaction} onChange={(event) => setAllergyForm((old) => ({ ...old, reaction: event.target.value }))} /><select className="form-input" value={allergyForm.severity} onChange={(event) => setAllergyForm((old) => ({ ...old, severity: event.target.value }))}><option value="UNKNOWN">Unknown severity</option><option value="MILD">Mild</option><option value="MODERATE">Moderate</option><option value="SEVERE">Severe</option></select><button className="button-secondary" disabled={busy}>Add allergy</button></form></section></div>
      <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-1"><HeartPulse size={18} /></span><div><h2>Medical history</h2><p>New entries append to the clinic record.</p></div></div>{detail.history.length ? <div className="record-list">{detail.history.map((item) => <article className="history-row" key={item.id}><div className="appointment-card-top"><strong>{item.category}</strong><time>{new Date(item.recordedAt).toLocaleString()}</time></div><p>{item.description}</p><small>Recorded by {item.recordedBy}</small></article>)}</div> : <p className="empty-state">No medical history entries are recorded.</p>}<form className="patient-add-form history-form" onSubmit={addHistory}><input className="form-input" value={historyForm.category} onChange={(event) => setHistoryForm((old) => ({ ...old, category: event.target.value }))} required maxLength={48} aria-label="History category" /><textarea className="form-input form-textarea" placeholder="Medical history, existing condition, or relevant note" value={historyForm.description} onChange={(event) => setHistoryForm((old) => ({ ...old, description: event.target.value }))} required maxLength={4000} /><button className="button-primary" disabled={busy}>Add history entry</button></form></section>
      <section className="workspace-panel mt-5"><div className="section-title"><div><h2>Previous consultations</h2><p>Finalized visit summaries from this clinic.</p></div></div>{detail.recentVisits.length ? <div className="record-list">{detail.recentVisits.map((visit) => <article className="record-card" key={visit.id}><div className="appointment-card-top"><strong>{visit.diagnosis || 'Consultation'}</strong><time>{visit.finalizedAt ? new Date(visit.finalizedAt).toLocaleDateString() : ''}</time></div><p className="prescription-instructions">{visit.symptoms || 'No symptoms entered'}{visit.treatmentPlan ? ' · ' + visit.treatmentPlan : ''}</p>{visit.doctorNotes && <p className="record-private-note">Care team note: {visit.doctorNotes}</p>}</article>)}</div> : <p className="empty-state">No completed consultations yet.</p>}</section>
      <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-2"><ShieldAlert size={18} /></span><div><h2>Medical reports</h2><p>Only authorized clinic users and this patient can request a temporary download link.</p></div></div><form className="report-upload-form" onSubmit={uploadReport}><label>Category<select className="form-input" value={reportCategory} onChange={(event) => setReportCategory(event.target.value)}><option value="LAB">Laboratory</option><option value="BLOOD_TEST">Blood test</option><option value="IMAGING">Imaging</option><option value="XRAY">X-ray</option><option value="OTHER">Other</option></select></label><label>Description<input className="form-input" maxLength={500} value={reportDescription} onChange={(event) => setReportDescription(event.target.value)} /></label><label>PDF or image<input className="form-input" type="file" accept="application/pdf,image/jpeg,image/png,image/webp" required onChange={(event) => setReportFile(event.target.files?.[0] || null)} /></label><button className="button-primary" disabled={busy}>{busy ? 'Uploading…' : 'Add report'}<Upload size={16} /></button></form>{reports.length ? <div className="record-list">{reports.map((report) => <article className="report-row" key={report.id}><div><strong>{report.originalFilename}</strong><p>{report.category}{report.description ? ' · ' + report.description : ''} · {new Date(report.createdAt).toLocaleDateString()}</p></div><button className="button-secondary" onClick={() => downloadReport(report)}><Download size={15} /> Open</button></article>)}</div> : <p className="empty-state">No reports recorded for this patient.</p>}</section>
    </>}
  </RoleLayout>;
}

function errorMessage(error) { return error.response?.data?.error || error.response?.data?.message || error.message || 'Request failed.'; }
