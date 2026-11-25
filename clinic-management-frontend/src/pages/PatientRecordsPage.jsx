import { useEffect, useState } from 'react';
import { ClipboardList, FileText, RefreshCw, Upload, Download } from 'lucide-react';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

export default function PatientRecordsPage() {
  const [consultations, setConsultations] = useState([]);
  const [prescriptions, setPrescriptions] = useState([]);
  const [clinics, setClinics] = useState([]);
  const [clinicId, setClinicId] = useState('');
  const [reports, setReports] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [reportCategory, setReportCategory] = useState('LAB');
  const [reportDescription, setReportDescription] = useState('');
  const [reportFile, setReportFile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let current = true;
    setLoading(true); setError('');
    Promise.all([api.get('/patient/consultations'), api.get('/patient/prescriptions'), api.get('/clinics')])
      .then(([visits, prescriptionsResponse, clinicResponse]) => { if (current) { setConsultations(visits.data); setPrescriptions(prescriptionsResponse.data); setClinics(clinicResponse.data); if (!clinicId && clinicResponse.data[0]) setClinicId(clinicResponse.data[0].id); } })
      .catch((cause) => { if (current) setError(errorMessage(cause)); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [refresh]);
  useEffect(() => {
    if (!clinicId) { setReports([]); return undefined; }
    let current = true;
    api.get('/patient/reports', { params: { clinicId } }).then(({ data }) => { if (current) setReports(data); })
      .catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, [clinicId, refresh]);
  async function uploadReport(event) {
    event.preventDefault(); if (!reportFile || !clinicId) return;
    setUploading(true); setError('');
    const formData = new FormData(); formData.append('file', reportFile);
    try {
      await api.post('/patient/reports', formData, { params: { clinicId, category: reportCategory, description: reportDescription || undefined }, headers: { 'Content-Type': 'multipart/form-data' } });
      setReportFile(null); setReportDescription(''); event.currentTarget.reset(); setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setUploading(false); }
  }
  async function downloadReport(report) {
    setError('');
    try { const { data } = await api.get(`/patient/reports/${report.id}/download`); window.open(data.url, '_blank', 'noopener,noreferrer'); }
    catch (cause) { setError(errorMessage(cause)); }
  }
  return <RoleLayout role="PATIENT">
    <div className="page-heading"><div><p className="eyebrow">PATIENT PORTAL</p><h1>Medical records</h1><p>Review completed visits, recorded vitals, and finalized prescriptions.</p></div><button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button></div>
    {error && <div className="inline-alert inline-alert-error" role="status">{error}</div>}
    {loading ? <section className="workspace-panel"><p className="empty-state">Loading your records…</p></section> : <>
      <section className="workspace-panel"><div className="section-title"><span className="metric-icon metric-icon-0"><ClipboardList size={18} /></span><div><h2>Completed consultations</h2><p>Your visit summaries across clinics.</p></div></div>
        {consultations.length ? <div className="record-list">{consultations.map((item) => <article className="record-card" key={item.id}><div className="appointment-card-top"><div><h3>{item.diagnosis || 'Consultation'}</h3><p>{item.clinicName} · {item.doctorName}</p></div><time>{item.finalizedAt ? new Date(item.finalizedAt).toLocaleDateString() : ''}</time></div><div className="record-grid">{item.symptoms && <RecordField title="Symptoms" value={item.symptoms} />}{item.observations && <RecordField title="Observations" value={item.observations} />}{item.treatmentPlan && <RecordField title="Treatment plan" value={item.treatmentPlan} />}{item.followUpInstructions && <RecordField title="Follow-up" value={item.followUpInstructions} />}{item.followUpDueAt && <RecordField title="Follow-up date" value={new Date(item.followUpDueAt).toLocaleString()} />}</div>{item.vitals && <p className="record-vitals">Vitals: {[item.vitals.heightCm && `Height ${item.vitals.heightCm} cm`, item.vitals.weightKg && `Weight ${item.vitals.weightKg} kg`, item.vitals.temperatureC && `Temp ${item.vitals.temperatureC} °C`, item.vitals.systolicBp && `BP ${item.vitals.systolicBp}/${item.vitals.diastolicBp}`, item.vitals.pulseBpm && `Pulse ${item.vitals.pulseBpm} bpm`, item.vitals.oxygenSaturation && `SpO₂ ${item.vitals.oxygenSaturation}%`].filter(Boolean).join(' · ')}</p>}</article>)}</div> : <p className="empty-state">Completed consultations will appear here.</p>}
      </section>
      <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-1"><FileText size={18} /></span><div><h2>Prescriptions</h2><p>Only finalized prescriptions are visible here.</p></div></div>
        {prescriptions.length ? <div className="record-list">{prescriptions.map((item) => { const current = item.amendments?.at(-1) || item; return <article className="record-card" key={item.id}><div className="appointment-card-top"><div><h3>{item.clinicName}</h3><p>{item.doctorName}</p></div><time>{item.finalizedAt ? new Date(item.finalizedAt).toLocaleDateString() : ''}</time></div>{item.amendments?.length > 0 && <p className="inline-alert inline-alert-success">Current prescription includes {item.amendments.length} documented amendment(s). Latest: {item.amendments.at(-1).reason}</p>}{current.instructions && <p className="prescription-instructions">{current.instructions}</p>}<div className="prescription-items">{current.items.map((medicine, index) => <div key={medicine.id || index}><strong>{medicine.medicineName}</strong>{medicine.genericName && <span> · {medicine.genericName}</span>}<p>{medicine.dosage}{medicine.doseUnit ? ' ' + medicine.doseUnit : ''} · {medicine.frequency} · {medicine.duration}{medicine.foodInstruction ? ' · ' + foodLabel(medicine.foodInstruction) : ''}</p>{medicine.additionalInstructions && <small>{medicine.additionalInstructions}</small>}</div>)}</div>{item.amendments?.length > 0 && <details className="appointment-details amendment-history"><summary>Amendment history</summary>{item.amendments.map((amendment) => <article key={amendment.id}><strong>{new Date(amendment.createdAt).toLocaleString()}</strong><p>{amendment.reason}</p></article>)}</details>}</article>; })}</div> : <p className="empty-state">Finalized prescriptions will appear here.</p>}
      </section>
      <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-2"><FileText size={18} /></span><div><h2>Medical reports</h2><p>PDF and image files are stored privately and opened through short-lived secure links.</p></div></div>
        {clinics.length > 1 && <label className="filter-label">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{clinics.map((clinic) => <option key={clinic.id} value={clinic.id}>{clinic.name}</option>)}</select></label>}
        <form className="report-upload-form" onSubmit={uploadReport}><label>Category<select className="form-input" value={reportCategory} onChange={(event) => setReportCategory(event.target.value)}><option value="LAB">Laboratory</option><option value="BLOOD_TEST">Blood test</option><option value="IMAGING">Imaging</option><option value="XRAY">X-ray</option><option value="OTHER">Other</option></select></label><label>Description<input className="form-input" maxLength={500} value={reportDescription} onChange={(event) => setReportDescription(event.target.value)} placeholder="Optional description" /></label><label>File<input className="form-input" type="file" accept="application/pdf,image/jpeg,image/png,image/webp" required onChange={(event) => setReportFile(event.target.files?.[0] || null)} /></label><button className="button-primary" disabled={uploading || !clinicId}>{uploading ? 'Uploading…' : 'Upload report'}<Upload size={16} /></button></form>
        {reports.length ? <div className="record-list">{reports.map((report) => <article className="report-row" key={report.id}><div><strong>{report.originalFilename}</strong><p>{report.category}{report.description ? ' · ' + report.description : ''} · {(report.sizeBytes / 1024 / 1024).toFixed(1)} MB</p><small>{new Date(report.createdAt).toLocaleString()}</small></div><button className="button-secondary" onClick={() => downloadReport(report)}><Download size={15} /> Open</button></article>)}</div> : <p className="empty-state">No reports are stored for this clinic yet.</p>}
      </section>
    </>}
  </RoleLayout>;
}

function RecordField({ title, value }) { return <div><strong>{title}</strong><p>{value}</p></div>; }
function foodLabel(value) { return ({ BEFORE_FOOD: 'Before food', AFTER_FOOD: 'After food', WITH_FOOD: 'With food', ANYTIME: 'Any time' })[value] || ''; }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.message || error.message || 'Could not load medical records.'; }
