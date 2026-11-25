import { useEffect, useMemo, useState } from 'react';
import { FileText, Plus, Printer, RefreshCw, Receipt, Trash2 } from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

const blankItem = () => ({ description: '', quantity: '1', unitPrice: '', taxRate: '0' });

export default function BillingPage({ role }) {
  const { session } = useAuth();
  const memberships = useMemo(() => session?.memberships?.filter((item) => item.role === role) || [], [session, role]);
  const [clinicId, setClinicId] = useState(memberships[0]?.clinicId || '');
  const [patients, setPatients] = useState([]);
  const [patientId, setPatientId] = useState('');
  const [invoices, setInvoices] = useState([]);
  const [items, setItems] = useState([blankItem()]);
  const [dueAt, setDueAt] = useState(defaultDueDate());
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [printInvoice, setPrintInvoice] = useState(null);
  useEffect(() => { if (memberships[0] && !clinicId) setClinicId(memberships[0].clinicId); }, [memberships, clinicId]);
  useEffect(() => {
    let current = true; setLoading(true); setError('');
    const request = role === 'PATIENT'
      ? api.get('/patient/invoices')
      : clinicId ? Promise.all([api.get('/billing/invoices', { params: { clinicId } }), api.get(role === 'DOCTOR' ? '/doctor/patients' : '/staff/patients', { params: { clinicId, q: '' } })]) : Promise.resolve([{ data: [] }, { data: [] }]);
    Promise.resolve(request).then((response) => {
      if (!current) return;
      if (role === 'PATIENT') setInvoices(response.data);
      else { setInvoices(response[0].data); setPatients(response[1].data); if (!patientId && response[1].data[0]) setPatientId(response[1].data[0].clinicPatientId); }
    }).catch((cause) => { if (current) setError(errorMessage(cause)); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [role, clinicId, refresh]);

  const subtotal = items.reduce((sum, item) => sum + (Number(item.quantity || 0) * Number(item.unitPrice || 0)), 0);
  const total = items.reduce((sum, item) => { const base = Number(item.quantity || 0) * Number(item.unitPrice || 0); return sum + base + (base * Number(item.taxRate || 0) / 100); }, 0);
  function updateItem(index, key, value) { setItems((old) => old.map((item, i) => i === index ? { ...item, [key]: value } : item)); }
  async function createInvoice(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      await api.post('/billing/invoices', { clinicPatientId: patientId, dueAt: new Date(dueAt).toISOString(), items: items.map((item) => ({ ...item, quantity: Number(item.quantity), unitPrice: Number(item.unitPrice), taxRate: Number(item.taxRate) })) }, { params: { clinicId } });
      setItems([blankItem()]); setNotice('Invoice issued.'); setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  async function recordPayment(invoiceId, amount, method, reference) {
    setError(''); setNotice('');
    try { await api.post(`/billing/invoices/${invoiceId}/payments`, { amount: Number(amount), method, reference: reference || null }, { params: { clinicId } }); setNotice('Payment recorded.'); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
  }
  function print(item) { setPrintInvoice(item); setTimeout(() => window.print(), 80); }

  return <RoleLayout role={role}>
    <div className="page-heading no-print"><div><p className="eyebrow">{role} PORTAL</p><h1>{role === 'PATIENT' ? 'Billing & payments' : 'Clinic billing'}</h1><p>{role === 'PATIENT' ? 'Review invoices and recorded payments for your visits.' : 'Issue itemized invoices and record clinic payments.'}</p></div><button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button></div>
    {role !== 'PATIENT' && memberships.length > 1 && <label className="filter-label no-print">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{memberships.map((item) => <option key={item.clinicId} value={item.clinicId}>{item.clinicName}</option>)}</select></label>}
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error no-print' : 'inline-alert inline-alert-success no-print'} role="status">{error || notice}</div>}
    {role !== 'PATIENT' && <section className="workspace-panel no-print"><div className="section-title"><span className="metric-icon metric-icon-0"><Plus size={18} /></span><div><h2>Create an invoice</h2><p>Issued invoices are retained with their payment history.</p></div></div><form className="invoice-form" onSubmit={createInvoice}>
      <label>Patient<select className="form-input" value={patientId} onChange={(event) => setPatientId(event.target.value)} required><option value="">Select patient</option>{patients.map((patient) => <option value={patient.clinicPatientId} key={patient.clinicPatientId}>{patient.fullName} · {patient.patientCode}</option>)}</select></label>
      <label>Due date<input className="form-input" type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} required /></label>
      <div className="invoice-items-editor">{items.map((item, index) => <div className="invoice-item-editor" key={index}><label>Description<input className="form-input" maxLength={240} value={item.description} onChange={(event) => updateItem(index, 'description', event.target.value)} required /></label><label>Qty<input className="form-input" type="number" min="0.01" step="0.01" value={item.quantity} onChange={(event) => updateItem(index, 'quantity', event.target.value)} required /></label><label>Unit price (₹)<input className="form-input" type="number" min="0" step="0.01" value={item.unitPrice} onChange={(event) => updateItem(index, 'unitPrice', event.target.value)} required /></label><label>Tax %<input className="form-input" type="number" min="0" max="100" step="0.01" value={item.taxRate} onChange={(event) => updateItem(index, 'taxRate', event.target.value)} required /></label><button className="button-secondary button-danger" type="button" disabled={items.length < 2} onClick={() => setItems((old) => old.filter((_, i) => i !== index))} aria-label="Remove item"><Trash2 size={15} /></button></div>)}</div>
      <div className="invoice-form-bottom"><button className="button-secondary" type="button" onClick={() => setItems((old) => [...old, blankItem()])}><Plus size={15} /> Add line</button><p>Subtotal ₹{subtotal.toFixed(2)} · Total ₹{total.toFixed(2)}</p><button className="button-primary" disabled={busy || !patientId}>{busy ? 'Issuing…' : 'Issue invoice'}<Receipt size={16} /></button></div>
    </form></section>}
    <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-1"><FileText size={18} /></span><div><h2>{role === 'PATIENT' ? 'Your invoices' : 'Recent invoices'}</h2><p>{invoices.length} invoice{invoices.length === 1 ? '' : 's'}.</p></div></div>
      {loading ? <p className="empty-state">Loading invoices…</p> : invoices.length ? <div className="invoice-list">{invoices.map((invoice) => <InvoiceCard key={invoice.id} invoice={invoice} patientView={role === 'PATIENT'} onPay={recordPayment} onPrint={print} />)}</div> : <p className="empty-state">Invoices will appear here when issued.</p>}
    </section>
    {printInvoice && <section className="invoice-print"><div className="print-clinic"><p>CLINIC INVOICE</p><h2>{printInvoice.invoiceNumber}</h2></div><div className="print-patient"><strong>Bill to:</strong> {printInvoice.patientName} ({printInvoice.patientCode})<br /><strong>Issued:</strong> {printInvoice.issuedAt ? new Date(printInvoice.issuedAt).toLocaleDateString() : ''}<br /><strong>Due:</strong> {printInvoice.dueAt ? new Date(printInvoice.dueAt).toLocaleDateString() : ''}</div><table><thead><tr><th>Description</th><th>Qty</th><th>Unit price</th><th>Tax</th><th>Total</th></tr></thead><tbody>{printInvoice.items.map((item) => <tr key={item.id}><td>{item.description}</td><td>{item.quantity}</td><td>₹{Number(item.unitPrice).toFixed(2)}</td><td>{item.taxRate}%</td><td>₹{Number(item.lineTotal).toFixed(2)}</td></tr>)}</tbody></table><div className="invoice-print-total"><span>Subtotal ₹{Number(printInvoice.subtotal).toFixed(2)}</span><span>Tax ₹{Number(printInvoice.taxTotal).toFixed(2)}</span><strong>Total ₹{Number(printInvoice.total).toFixed(2)}</strong><span>Paid ₹{Number(printInvoice.paid).toFixed(2)}</span><strong>Balance ₹{Number(printInvoice.balance).toFixed(2)}</strong></div><p className="print-signature">Authorized signature</p></section>}
  </RoleLayout>;
}

function InvoiceCard({ invoice, patientView, onPay, onPrint }) {
  const [amount, setAmount] = useState(String(invoice.balance));
  const [method, setMethod] = useState('CASH');
  const [reference, setReference] = useState('');
  return <article className="invoice-card"><div className="appointment-card-top"><div><h3>{invoice.invoiceNumber}</h3><p>{invoice.patientName} · {invoice.patientCode}</p></div><span className={'status-pill status-' + invoice.status.toLowerCase().replaceAll('_', '-')}>{formatStatus(invoice.status)}</span></div><div className="invoice-summary"><span>Total <strong>₹{Number(invoice.total).toFixed(2)}</strong></span><span>Paid <strong>₹{Number(invoice.paid).toFixed(2)}</strong></span><span>Balance <strong>₹{Number(invoice.balance).toFixed(2)}</strong></span><span>Due <strong>{invoice.dueAt ? new Date(invoice.dueAt).toLocaleDateString() : '—'}</strong></span></div><div className="invoice-lines">{invoice.items.map((item) => <div key={item.id}><span>{item.description} × {item.quantity}</span><strong>₹{Number(item.lineTotal).toFixed(2)}</strong></div>)}</div>{invoice.payments.length > 0 && <details className="appointment-details"><summary>Payment history ({invoice.payments.length})</summary><div className="payment-list">{invoice.payments.map((payment) => <p key={payment.id}>₹{Number(payment.amount).toFixed(2)} · {payment.method}{payment.reference ? ' · ' + payment.reference : ''} · {new Date(payment.receivedAt).toLocaleString()}</p>)}</div></details>}<div className="invoice-actions"><button className="button-secondary" onClick={() => onPrint(invoice)}><Printer size={15} /> Print / Save PDF</button>{!patientView && invoice.balance > 0 && ['ISSUED', 'PARTIALLY_PAID'].includes(invoice.status) && <div className="invoice-payment"><input className="form-input" type="number" min="0.01" max={invoice.balance} step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} aria-label="Payment amount" /><select className="form-input" value={method} onChange={(event) => setMethod(event.target.value)} aria-label="Payment method"><option value="CASH">Cash</option><option value="UPI">UPI</option><option value="CARD">Card</option><option value="BANK_TRANSFER">Bank transfer</option><option value="ONLINE">Online</option></select><input className="form-input" maxLength={120} placeholder="Reference (optional)" value={reference} onChange={(event) => setReference(event.target.value)} /><button className="button-primary" onClick={() => onPay(invoice.id, amount, method, reference)}>Record payment</button></div>}</div></article>;
}
function defaultDueDate() { const date = new Date(Date.now() + 86400000); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 16); }
function formatStatus(value) { return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase()); }
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.message || error.message || 'Request failed.'; }
