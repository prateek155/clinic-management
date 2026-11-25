import { useState } from 'react';
import { ArrowRight, MailCheck, ShieldCheck } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { api } from '../services/api.js';

export default function AcceptStaffInvitationPage() {
  const [search] = useSearchParams();
  const token = search.get('token') || '';
  const { user, session, refreshSession, sendVerificationEmail } = useAuth();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [verificationSent, setVerificationSent] = useState(false);
  const loginUrl = `/login?returnTo=${encodeURIComponent(`/staff/accept-invitation?token=${encodeURIComponent(token)}`)}`;
  async function accept() {
    setBusy(true); setError(''); setNotice('');
    try { await api.post('/staff/invitations/accept', { token }); await refreshSession(); setNotice('Clinic staff access is active.'); setTimeout(() => navigate('/staff', { replace: true }), 650); }
    catch (cause) { setError(cause.response?.data?.error || cause.response?.data?.message || cause.message || 'Invitation could not be accepted.'); }
    finally { setBusy(false); }
  }
  async function sendVerification() {
    setBusy(true); setError(''); setNotice('');
    try { await sendVerificationEmail(); setVerificationSent(true); setNotice(`Verification email sent to ${user.email}. Open it, then return here and check again.`); }
    catch (cause) { setError(cause.message || 'Could not send verification email.'); }
    finally { setBusy(false); }
  }
  async function checkVerification() {
    setBusy(true); setError(''); setNotice('');
    try { await refreshSession(); if (!user.emailVerified) setNotice('Firebase still reports this email as unverified. Open the verification link, then check again.'); }
    catch (cause) { setError(cause.message || 'Could not refresh your account status.'); }
    finally { setBusy(false); }
  }
  return <main className="invite-accept-shell"><section className="workspace-panel invite-accept-card"><span className="brand-mark"><ShieldCheck size={21} /></span><p className="eyebrow">CAREFLOW CLINIC</p><h1>Accept staff invitation</h1><p className="invite-copy">This invitation adds clinic staff access using permissions selected by the clinic doctor.</p>{error && <div className="inline-alert inline-alert-error">{error}</div>}{notice && <div className="inline-alert inline-alert-success">{notice}</div>}
    {!token ? <p className="empty-state">This invitation link is missing its token. Ask the clinic to send a fresh invitation.</p> : user && session ? <><p className="invite-account">Signed in as <strong>{session.email}</strong></p>{user.emailVerified ? <button className="button-primary w-full" onClick={accept} disabled={busy}>{busy ? 'Accepting…' : 'Accept invitation'}<ArrowRight size={16} /></button> : <div className="invite-verification"><p>Verify this Firebase email before receiving clinic access.</p><button className="button-primary w-full" onClick={verificationSent ? checkVerification : sendVerification} disabled={busy}>{busy ? 'Checking…' : verificationSent ? 'I verified my email' : 'Send verification email'}<MailCheck size={16} /></button></div>}<button className="invite-signout" onClick={() => navigate('/login')}>Use a different Firebase account</button></> : <><p className="invite-account">Sign in or create a Firebase account with the email address this invitation was sent to.</p><Link className="button-primary w-full" to={loginUrl}>Sign in or create account<ArrowRight size={16} /></Link></>}
  </section></main>;
}
