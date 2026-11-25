import { useEffect, useState } from 'react';
import { ArrowLeft, FileText, Plus, Printer, Save, Trash2 } from 'lucide-react';
import { Link, useSearchParams, useParams } from 'react-router-dom';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

const emptyMedicine = () => ({ medicineName: '', genericName: '', dosage: '', doseUnit: '', frequency: '', duration: '', route: '', foodInstruction: '', quantity: '', additionalInstructions: '', startsOn: '', endsOn: '' });
const emptyVitals = { heightCm: '', weightKg: '', temperatureC: '', systolicBp: '', diastolicBp: '', pulseBpm: '', oxygenSaturation: '' };

export default function DoctorConsultationPage() {
  const { appointmentId } = useParams();
  const [search] = useSearchParams();
  const clinicId = search.get('clinicId') || '';
  const [appointment, setAppointment] = useState(null);
  const [consultation, setConsultation] = useState(null);
  const [prescription, setPrescription] = useState(null);
  const [prescriptionInstructions, setPrescriptionInstructions] = useState('');
  const [amendmentMode, setAmendmentMode] = useState(false);
  const [amendmentReason, setAmendmentReason] = useState('');
  const [form, setForm] = useState({ symptoms: '', observations: '', diagnosis: '', treatmentPlan: '', doctorNotes: '', followUpInstructions: '', followUpDueAt: '' });
  const [vitals, setVitals] = useState(emptyVitals);
  const [medicines, setMedicines] = useState([emptyMedicine()]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let current = true;
    if (!clinicId) { setError('Choose a clinic from the doctor portal first.'); setLoading(false); return undefined; }
    setLoading(true); setError('');
    async function load() {
      try {
        const { data: visit } = await api.get(`/doctor/appointments/${appointmentId}`, { params: { clinicId } });
        if (!current) return;
        setAppointment(visit);
        if (!['IN_CONSULTATION', 'COMPLETED'].includes(visit.status)) {
          setConsultation(null);
          return;
        }
        let consult;
        try {
          ({ data: consult } = await api.get(`/doctor/appointments/${appointmentId}/consultation`, { params: { clinicId } }));
        } catch (cause) {
          if (visit.status !== 'IN_CONSULTATION' || cause.response?.status !== 404) throw cause;
          ({ data: consult } = await api.post(`/doctor/appointments/${appointmentId}/consultation`, null, { params: { clinicId } }));
        }
        if (!current) return;
        setConsultation(consult);
        setForm({ symptoms: consult.symptoms || '', observations: consult.observations || '', diagnosis: consult.diagnosis || '', treatmentPlan: consult.treatmentPlan || '', doctorNotes: consult.doctorNotes || '', followUpInstructions: consult.followUpInstructions || '', followUpDueAt: toLocalInput(consult.followUpDueAt) });
        setVitals(Object.fromEntries(Object.keys(emptyVitals).map((key) => [key, consult.vitals?.[key] ?? ''])));
        try {
          const { data } = await api.get(`/doctor/consultations/${consult.id}/prescription`, { params: { clinicId } });
          if (current) {
            setPrescription(data);
            const effective = data.amendments?.at(-1) || data;
            setPrescriptionInstructions(effective.instructions || '');
            setMedicines(effective.items?.length ? effective.items.map((item) => ({ ...emptyMedicine(), ...item, startsOn: item.startsOn || '', endsOn: item.endsOn || '' })) : [emptyMedicine()]);
          }
        } catch (cause) { if (cause.response?.status !== 404) throw cause; if (current) { setPrescription(null); setPrescriptionInstructions(''); setMedicines([emptyMedicine()]); } }
      } catch (cause) { if (current) setError(errorMessage(cause)); }
      finally { if (current) setLoading(false); }
    }
    load();
    return () => { current = false; };
  }, [appointmentId, clinicId, refresh]);

  function updateForm(key, value) { setForm((old) => ({ ...old, [key]: value })); }
  function updateVitals(key, value) { setVitals((old) => ({ ...old, [key]: value })); }
  function updateMedicine(index, key, value) { setMedicines((old) => old.map((item, i) => i === index ? { ...item, [key]: value } : item)); }

  function consultationPayload() {
    const numericVitals = Object.fromEntries(Object.entries(vitals).map(([key, value]) => [key, value === '' ? null : Number(value)]));
    return { ...form, followUpDueAt: form.followUpDueAt ? new Date(form.followUpDueAt).toISOString() : null, vitals: Object.values(numericVitals).some((value) => value !== null) ? numericVitals : null };
  }

  async function saveConsultation() {
    if (!consultation) return null;
    setBusy(true); setError(''); setNotice('');
    try {
      const { data } = await api.put(`/doctor/consultations/${consultation.id}`, consultationPayload(), { params: { clinicId } });
      setConsultation(data); setNotice('Consultation draft saved.'); return data;
    } catch (cause) { setError(errorMessage(cause)); return null; }
    finally { setBusy(false); }
  }

  async function savePrescription() {
    if (!consultation) return;
    const items = prescriptionItems();
    if (!items) return;
    if (!items.length) { setError('Add at least one medicine before saving the prescription.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      const { data } = await api.put(`/doctor/consultations/${consultation.id}/prescription`, { instructions: blankToNull(prescriptionInstructions), items }, { params: { clinicId } });
      setPrescription(data); setNotice('Prescription draft saved.');
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  function prescriptionItems() {
    const filled = medicines.filter((item) => item.medicineName.trim());
    if (medicines.some((item) => item.medicineName.trim() && (!item.dosage.trim() || !item.frequency.trim() || !item.duration.trim()))) {
      setError('For each medicine, enter the dose, frequency, and duration.'); return null;
    }
    return filled.map((item) => ({ ...item, genericName: blankToNull(item.genericName), doseUnit: blankToNull(item.doseUnit), route: blankToNull(item.route), foodInstruction: blankToNull(item.foodInstruction), quantity: blankToNull(item.quantity), additionalInstructions: blankToNull(item.additionalInstructions), startsOn: blankToNull(item.startsOn), endsOn: blankToNull(item.endsOn) }));
  }

  function startAmendment() {
    const latest = prescription?.amendments?.at(-1) || prescription;
    setPrescriptionInstructions(latest?.instructions || '');
    setMedicines(latest?.items?.length ? latest.items.map((item) => ({ ...emptyMedicine(), ...item, startsOn: item.startsOn || '', endsOn: item.endsOn || '' })) : [emptyMedicine()]);
    setAmendmentReason(''); setAmendmentMode(true); setError(''); setNotice('');
  }

  async function saveAmendment() {
    if (!prescription?.id) return;
    if (!amendmentReason.trim()) { setError('Enter why this finalized prescription is being amended.'); return; }
    const items = prescriptionItems();
    if (!items) return;
    if (!items.length) { setError('An amendment must contain at least one medicine.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      const { data } = await api.post(`/doctor/prescriptions/${prescription.id}/amendments`, {
        reason: amendmentReason.trim(), changes: { instructions: blankToNull(prescriptionInstructions), items },
      }, { params: { clinicId } });
      setPrescription(data); const latest = data.amendments?.at(-1);
      if (latest) { setPrescriptionInstructions(latest.instructions || ''); setMedicines(latest.items.map((item) => ({ ...emptyMedicine(), ...item, startsOn: item.startsOn || '', endsOn: item.endsOn || '' }))); }
      setAmendmentMode(false); setAmendmentReason(''); setNotice('Prescription amendment saved with an append-only history entry.');
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function finalizePrescription() {
    if (!prescription?.id) return;
    if (!window.confirm('Finalize this prescription? Its medicines will become read-only.')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const { data } = await api.patch(`/doctor/prescriptions/${prescription.id}/finalize`, null, { params: { clinicId } });
      setPrescription(data); setNotice('Prescription finalized. You can now print or save it as PDF.');
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function finalizeConsultation() {
    if (!consultation?.id || !window.confirm('Finalize this consultation and mark the appointment complete?')) return;
    const saved = await saveConsultation();
    if (!saved) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const { data } = await api.patch(`/doctor/consultations/${consultation.id}/finalize`, null, { params: { clinicId } });
      setConsultation(data); setNotice('Visit finalized and recorded in patient history.'); setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  const locked = Boolean(consultation?.finalizedAt);
  const prescriptionReadOnly = Boolean(locked || prescription?.finalizedAt) && !amendmentMode;
  const effectivePrescription = prescription?.amendments?.at(-1) || prescription;
  return <RoleLayout role="DOCTOR">
    <div className="page-heading no-print"><div><p className="eyebrow">DOCTOR PORTAL</p><h1>Consultation</h1><p>Record this visit and prepare a prescription for the patient.</p></div><Link className="button-secondary" to="/doctor/appointments"><ArrowLeft size={16} /> Appointments</Link></div>
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error no-print' : 'inline-alert inline-alert-success no-print'} role="status">{error || notice}</div>}
    {loading ? <section className="workspace-panel"><p className="empty-state">Loading consultation…</p></section> : !appointment ? <section className="workspace-panel"><p className="empty-state">Appointment could not be loaded.</p></section> : <>
      <section className="workspace-panel consultation-patient"><div><p className="eyebrow">PATIENT</p><h2>{appointment.patientName}</h2><p>{appointment.patientCode} · {appointment.patientEmail}</p><Link className="patient-record-link" to={`/doctor/patients/${appointment.clinicPatientId}?clinicId=${clinicId}`}>Review patient history</Link></div><div><p className="eyebrow">VISIT</p><h2>{new Date(appointment.startsAt).toLocaleDateString([], { dateStyle: 'medium' })}</h2><p>{new Date(appointment.startsAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })} · {appointment.clinicName}</p></div><span className={'status-pill status-' + appointment.status.toLowerCase()}>{formatStatus(appointment.status)}</span></section>
      {!consultation ? <section className="workspace-panel mt-5"><p className="empty-state">Start the appointment from the doctor appointments list before entering a consultation.</p><Link className="button-secondary" to="/doctor/appointments">Return to appointments</Link></section> : <>
        <section className="workspace-panel mt-5 no-print"><div className="section-title"><span className="metric-icon metric-icon-0"><FileText size={18} /></span><div><h2>Clinical notes</h2><p>{locked ? 'Finalized consultations are kept as read-only history.' : 'Save a draft as you work. Finalizing preserves the visit in the patient record.'}</p></div></div>
          <div className="clinical-grid">
            <TextField label="Symptoms" value={form.symptoms} onChange={(value) => updateForm('symptoms', value)} disabled={locked} />
            <TextField label="Observations" value={form.observations} onChange={(value) => updateForm('observations', value)} disabled={locked} />
            <TextField label="Diagnosis" value={form.diagnosis} onChange={(value) => updateForm('diagnosis', value)} disabled={locked} />
            <TextField label="Treatment plan" value={form.treatmentPlan} onChange={(value) => updateForm('treatmentPlan', value)} disabled={locked} />
            <TextField label="Doctor notes (private to care team)" value={form.doctorNotes} onChange={(value) => updateForm('doctorNotes', value)} disabled={locked} />
            <TextField label="Follow-up instructions" value={form.followUpInstructions} onChange={(value) => updateForm('followUpInstructions', value)} disabled={locked} />
          </div>
          <h3 className="subsection-heading">Vitals</h3><div className="vitals-grid">
            <NumberField label="Height (cm)" value={vitals.heightCm} onChange={(value) => updateVitals('heightCm', value)} disabled={locked} />
            <NumberField label="Weight (kg)" value={vitals.weightKg} onChange={(value) => updateVitals('weightKg', value)} disabled={locked} />
            <NumberField label="Temperature (°C)" value={vitals.temperatureC} onChange={(value) => updateVitals('temperatureC', value)} disabled={locked} />
            <NumberField label="Systolic BP" value={vitals.systolicBp} onChange={(value) => updateVitals('systolicBp', value)} disabled={locked} />
            <NumberField label="Diastolic BP" value={vitals.diastolicBp} onChange={(value) => updateVitals('diastolicBp', value)} disabled={locked} />
            <NumberField label="Pulse (bpm)" value={vitals.pulseBpm} onChange={(value) => updateVitals('pulseBpm', value)} disabled={locked} />
            <NumberField label="Oxygen saturation (%)" value={vitals.oxygenSaturation} onChange={(value) => updateVitals('oxygenSaturation', value)} disabled={locked} />
          </div>
          <div className="vitals-grid followup-row"><label>Follow-up date and time<input className="form-input" type="datetime-local" value={form.followUpDueAt} onChange={(event) => updateForm('followUpDueAt', event.target.value)} disabled={locked} /></label></div>
          {!locked && <div className="form-actions"><button className="button-secondary" onClick={saveConsultation} disabled={busy}><Save size={16} /> Save draft</button><button className="button-primary" onClick={finalizeConsultation} disabled={busy}>Finalize visit</button></div>}
        </section>
        <section className="workspace-panel mt-5 no-print"><div className="section-title"><span className="metric-icon metric-icon-1"><FileText size={18} /></span><div><h2>Prescription</h2><p>{amendmentMode ? 'Add the corrected content and a reason. Earlier versions remain in history.' : 'Finalized prescriptions are immutable; corrections are recorded as separate amendments.'}</p></div></div>
          <label className="clinical-field">Prescription instructions<textarea className="form-input form-textarea" maxLength={2000} value={prescriptionInstructions} onChange={(event) => setPrescriptionInstructions(event.target.value)} disabled={prescriptionReadOnly} /></label>
          {amendmentMode && <label className="clinical-field amendment-reason">Reason for amendment<textarea className="form-input form-textarea" maxLength={500} required value={amendmentReason} onChange={(event) => setAmendmentReason(event.target.value)} /></label>}
          {medicines.map((item, index) => <div className="medicine-editor" key={index}><div className="medicine-editor-heading"><strong>Medicine {index + 1}</strong>{!prescriptionReadOnly && <button className="button-secondary button-danger" type="button" onClick={() => setMedicines((old) => old.filter((_, i) => i !== index))} aria-label="Remove medicine"><Trash2 size={15} /> Remove</button>}</div><div className="medicine-grid">
            <TextInput label="Medicine name" value={item.medicineName} onChange={(value) => updateMedicine(index, 'medicineName', value)} disabled={prescriptionReadOnly} />
            <TextInput label="Generic name" value={item.genericName} onChange={(value) => updateMedicine(index, 'genericName', value)} disabled={prescriptionReadOnly} />
            <TextInput label="Dose" value={item.dosage} onChange={(value) => updateMedicine(index, 'dosage', value)} disabled={prescriptionReadOnly} />
            <TextInput label="Dose unit" value={item.doseUnit} onChange={(value) => updateMedicine(index, 'doseUnit', value)} disabled={prescriptionReadOnly} />
            <TextInput label="Frequency" value={item.frequency} onChange={(value) => updateMedicine(index, 'frequency', value)} disabled={prescriptionReadOnly} />
            <TextInput label="Duration" value={item.duration} onChange={(value) => updateMedicine(index, 'duration', value)} disabled={prescriptionReadOnly} />
            <TextInput label="Route" value={item.route} onChange={(value) => updateMedicine(index, 'route', value)} disabled={prescriptionReadOnly} />
            <label>Food instruction<select className="form-input" value={item.foodInstruction} onChange={(event) => updateMedicine(index, 'foodInstruction', event.target.value)} disabled={prescriptionReadOnly}><option value="">Not specified</option><option value="BEFORE_FOOD">Before food</option><option value="AFTER_FOOD">After food</option><option value="WITH_FOOD">With food</option><option value="ANYTIME">Any time</option></select></label>
            <TextInput label="Quantity" value={item.quantity} onChange={(value) => updateMedicine(index, 'quantity', value)} disabled={prescriptionReadOnly} />
            <TextInput label="Additional instructions" value={item.additionalInstructions} onChange={(value) => updateMedicine(index, 'additionalInstructions', value)} disabled={prescriptionReadOnly} />
            <label>Start date<input className="form-input" type="date" value={item.startsOn} onChange={(event) => updateMedicine(index, 'startsOn', event.target.value)} disabled={prescriptionReadOnly} /></label>
            <label>End date<input className="form-input" type="date" value={item.endsOn} onChange={(event) => updateMedicine(index, 'endsOn', event.target.value)} disabled={prescriptionReadOnly} /></label>
          </div></div>)}
          {!prescriptionReadOnly && <div className="form-actions"><button className="button-secondary" type="button" onClick={() => setMedicines((old) => [...old, emptyMedicine()])}><Plus size={16} /> Add medicine</button>{amendmentMode ? <><button className="button-secondary" type="button" onClick={() => setAmendmentMode(false)}>Cancel amendment</button><button className="button-primary" type="button" onClick={saveAmendment} disabled={busy}><Save size={16} /> Save amendment</button></> : <><button className="button-secondary" type="button" onClick={savePrescription} disabled={busy}><Save size={16} /> Save prescription</button>{prescription?.id && <button className="button-primary" type="button" onClick={finalizePrescription} disabled={busy}>Finalize prescription</button>}</>}</div>}
          {prescription?.finalizedAt && <div className="form-actions"><button className="button-secondary" onClick={() => window.print()}><Printer size={16} /> Print / Save as PDF</button>{!amendmentMode && <button className="button-secondary" onClick={startAmendment}>Create amendment</button>}<span className="muted-label">Finalized {new Date(prescription.finalizedAt).toLocaleString()}</span></div>}
          {prescription?.amendments?.length > 0 && <details className="appointment-details amendment-history"><summary>Amendment history ({prescription.amendments.length})</summary>{prescription.amendments.map((item) => <article key={item.id}><strong>{new Date(item.createdAt).toLocaleString()}</strong><p>{item.reason}</p></article>)}</details>}
        </section>
        {prescription?.finalizedAt && <section className="prescription-print workspace-panel mt-5"><div className="print-clinic"><p>MEDICAL PRESCRIPTION</p><h2>{appointment.clinicName}</h2><span>{appointment.doctorName}</span></div><div className="print-patient"><strong>Patient:</strong> {appointment.patientName} <span>({appointment.patientCode})</span><br /><strong>Date:</strong> {new Date(prescription.finalizedAt).toLocaleDateString()}</div>{effectivePrescription?.instructions && <p>{effectivePrescription.instructions}</p>}<div className="print-medicine-list">{(effectivePrescription?.items || []).map((item, index) => <article key={item.id || index}><b>{index + 1}. {item.medicineName}</b>{item.genericName && <span> · {item.genericName}</span>}<p>{item.dosage}{item.doseUnit ? ' ' + item.doseUnit : ''} · {item.frequency} · {item.duration}</p><p>{[item.route, foodLabel(item.foodInstruction), item.quantity ? 'Qty ' + item.quantity : '', item.additionalInstructions].filter(Boolean).join(' · ')}</p></article>)}</div>{prescription.amendments?.length > 0 && <p>Current version includes {prescription.amendments.length} documented amendment(s).</p>}<div className="print-signature">Doctor signature</div></section>}
      </>}
    </>}
  </RoleLayout>;
}

function TextField({ label, value, onChange, disabled }) { return <label className="clinical-field">{label}<textarea className="form-input form-textarea" maxLength={4000} value={value} onChange={(event) => onChange(event.target.value)} disabled={disabled} /></label>; }
function TextInput({ label, value, onChange, disabled }) { return <label>{label}<input className="form-input" value={value} onChange={(event) => onChange(event.target.value)} disabled={disabled} /></label>; }
function NumberField({ label, value, onChange, disabled }) { return <label>{label}<input className="form-input" type="number" step="any" value={value} onChange={(event) => onChange(event.target.value)} disabled={disabled} /></label>; }
function toLocalInput(value) { if (!value) return ''; const date = new Date(value); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 16); }
function blankToNull(value) { return value?.trim() ? value.trim() : null; }
function foodLabel(value) { return ({ BEFORE_FOOD: 'Before food', AFTER_FOOD: 'After food', WITH_FOOD: 'With food', ANYTIME: 'Any time' })[value] || ''; }
function formatStatus(value) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase()); }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.message || error.message || 'Request failed.'; }
