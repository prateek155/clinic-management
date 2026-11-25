import { useEffect, useMemo, useState } from 'react';
import { Copy, RefreshCw, ShieldCheck, UserPlus, UserX } from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import RoleLayout from '../layouts/RoleLayout.jsx';
import { api } from '../services/api.js';

const options = [
  ['PATIENT_READ', 'View patients'], ['PATIENT_REGISTER', 'Register patients'],
  ['APPOINTMENT_READ', 'View appointments'], ['APPOINTMENT_MANAGE', 'Manage appointments'],
  ['QUEUE_MANAGE', 'Manage waiting queue'], ['VITALS_WRITE', 'Record vitals'],
  ['BILLING_READ', 'View billing'], ['BILLING_WRITE', 'Manage billing'], ['REPORT_UPLOAD', 'Upload reports'],
];

export default function DoctorStaffPage() {
  const { session } = useAuth();
  const clinics = useMemo(() => session?.memberships?.filter((item) => item.role === 'DOCTOR') || [], [session]);
  const [clinicId, setClinicId] = useState(clinics[0]?.clinicId || '');
  const [members, setMembers] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [email, setEmail] = useState('');
  const [designation, setDesignation] = useState('Receptionist');
  const [permissions, setPermissions] = useState(['PATIENT_READ', 'APPOINTMENT_READ', 'QUEUE_MANAGE']);
  const [inviteLink, setInviteLink] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => { if (clinics[0] && !clinicId) setClinicId(clinics[0].clinicId); }, [clinics, clinicId]);
  useEffect(() => {
    if (!clinicId) return undefined;
    let current = true;
    Promise.all([api.get('/doctor/staff', { params: { clinicId } }), api.get('/doctor/staff/invitations', { params: { clinicId } })])
      .then(([staffResponse, invitationResponse]) => { if (current) { setMembers(staffResponse.data); setInvitations(invitationResponse.data); } })
      .catch((cause) => { if (current) setError(errorMessage(cause)); });
    return () => { current = false; };
  }, [clinicId, refresh]);

  function togglePermission(permission) { setPermissions((old) => old.includes(permission) ? old.filter((item) => item !== permission) : [...old, permission]); }
  async function invite(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice(''); setInviteLink('');
    try {
      const { data } = await api.post('/doctor/staff/invitations', { email, designation, permissions }, { params: { clinicId } });
      const link = `${window.location.origin}/staff/accept-invitation?token=${encodeURIComponent(data.token)}`;
      setInviteLink(link); setNotice(`Invitation created for ${data.email}; it expires ${new Date(data.expiresAt).toLocaleString()}. Share this link with the invited person.`);
      setEmail(''); setRefresh((value) => value + 1);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function updateMember(member, nextPermissions) {
    setError(''); setNotice('');
    try { await api.put(`/doctor/staff/${member.memberId}/permissions`, { permissions: nextPermissions }, { params: { clinicId } }); setNotice(`Permissions updated for ${member.fullName}.`); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
  }

  async function deactivate(member) {
    if (!window.confirm(`Revoke ${member.fullName}'s access to this clinic?`)) return;
    setError(''); setNotice('');
    try { await api.delete(`/doctor/staff/${member.memberId}`, { params: { clinicId } }); setNotice(`${member.fullName}'s clinic access was revoked.`); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
  }

  async function revokeInvitation(item) {
    if (!window.confirm(`Revoke the pending invitation for ${item.email}?`)) return;
    setError(''); setNotice('');
    try { await api.delete(`/doctor/staff/invitations/${item.id}`, { params: { clinicId } }); setNotice(`Invitation for ${item.email} was revoked.`); setRefresh((value) => value + 1); }
    catch (cause) { setError(errorMessage(cause)); }
  }

  return <RoleLayout role="DOCTOR">
    <div className="page-heading"><div><p className="eyebrow">DOCTOR PORTAL</p><h1>Staff access</h1><p>Invite clinic staff and grant only the permissions their work needs.</p></div><button className="button-secondary" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={16} /> Refresh</button></div>
    {clinics.length > 1 && <label className="filter-label">Clinic<select className="form-input" value={clinicId} onChange={(event) => setClinicId(event.target.value)}>{clinics.map((item) => <option key={item.clinicId} value={item.clinicId}>{item.clinicName}</option>)}</select></label>}
    {(error || notice) && <div className={error ? 'inline-alert inline-alert-error' : 'inline-alert inline-alert-success'} role="status">{error || notice}</div>}
    {inviteLink && <section className="workspace-panel invite-link-panel"><label>Invitation link<input className="form-input" readOnly value={inviteLink} onFocus={(event) => event.target.select()} /></label><button className="button-secondary" onClick={async () => { await navigator.clipboard.writeText(inviteLink); setNotice('Invitation link copied.'); }}><Copy size={15} /> Copy link</button><p>Send this link to the invitee using your usual secure channel. They must create or sign in to Firebase using the invited email.</p></section>}
    <section className="workspace-panel"><div className="section-title"><span className="metric-icon metric-icon-0"><UserPlus size={18} /></span><div><h2>Invite staff member</h2><p>An invitation link is valid for seven days and can be accepted once.</p></div></div>
      <form className="staff-invite-form" onSubmit={invite}><label>Email<input className="form-input" type="email" required maxLength={320} value={email} onChange={(event) => setEmail(event.target.value)} /></label><label>Designation<input className="form-input" maxLength={100} value={designation} onChange={(event) => setDesignation(event.target.value)} /></label><div className="permissions-grid">{options.map(([value, label]) => <label key={value}><input type="checkbox" checked={permissions.includes(value)} onChange={() => togglePermission(value)} />{label}</label>)}</div><button className="button-primary" disabled={busy || !clinicId || !permissions.length}>{busy ? 'Creating…' : 'Create invitation'}<UserPlus size={16} /></button></form>
    </section>
    <section className="workspace-panel mt-5"><div className="section-title"><span className="metric-icon metric-icon-1"><ShieldCheck size={18} /></span><div><h2>Clinic team</h2><p>Change permissions or revoke clinic access.</p></div></div>
      {members.length ? <div className="staff-list">{members.map((member) => <StaffCard key={member.memberId} member={member} onSave={updateMember} onDeactivate={deactivate} />)}</div> : <p className="empty-state">No staff accounts have accepted an invitation yet.</p>}
    </section>
    <section className="workspace-panel mt-5"><div className="section-title"><div><h2>Invitations</h2><p>Invitation links can be revoked before they are accepted.</p></div></div>{invitations.length ? <div className="staff-list">{invitations.map((item) => { const status = item.acceptedAt ? 'Accepted' : item.revokedAt ? 'Revoked' : new Date(item.expiresAt) < new Date() ? 'Expired' : 'Pending'; return <article className="schedule-card" key={item.id}><div><h3>{item.email}</h3><p>{item.designation || 'Staff'} · {item.permissions.join(', ')}</p></div><div className="service-row-actions"><span className="status-pill">{status}</span>{status === 'Pending' && <button className="button-secondary button-danger" onClick={() => revokeInvitation(item)}><UserX size={14} /> Revoke</button>}</div></article>; })}</div> : <p className="empty-state">No invitations have been created.</p>}</section>
  </RoleLayout>;
}

function StaffCard({ member, onSave, onDeactivate }) {
  const [draft, setDraft] = useState(member.permissions || []);
  useEffect(() => setDraft(member.permissions || []), [member.permissions]);
  return <article className={`staff-card ${!member.active ? 'staff-inactive' : ''}`}><div className="staff-card-head"><div><h3>{member.fullName}</h3><p>{member.email} · {member.designation || 'Staff'}</p></div><span className={'status-pill ' + (member.active ? 'status-confirmed' : 'status-cancelled')}>{member.active ? 'Active' : 'Inactive'}</span></div><div className="permissions-grid">{options.map(([value, label]) => <label key={value}><input type="checkbox" checked={draft.includes(value)} disabled={!member.active} onChange={() => setDraft((old) => old.includes(value) ? old.filter((item) => item !== value) : [...old, value])} />{label}</label>)}</div><div className="form-actions"><button className="button-secondary" disabled={!member.active || !draft.length} onClick={() => onSave(member, draft)}>Save permissions</button>{member.active && <button className="button-secondary button-danger" onClick={() => onDeactivate(member)}><UserX size={15} /> Revoke access</button>}</div></article>;
}
function errorMessage(error) { return error.response?.data?.error || error.response?.data?.message || error.message || 'Request failed.'; }
