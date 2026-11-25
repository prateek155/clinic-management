import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Activity, ArrowRight, Globe, ShieldCheck, Stethoscope } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { homeForRoles } from '../utils/roles.js';

const schema = z.object({
  fullName: z.string().optional(),
  email: z.string().email('Enter a valid email address.'),
  password: z.string().min(8, 'Use at least 8 characters.'),
});

export default function LoginPage() {
  const [registering, setRegistering] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { session, sessionError, signIn, register, signInWithGoogle, firebaseConfigured } = useAuth();
  const { register: field, handleSubmit, setError: setFieldError, formState: { errors } } = useForm({ resolver: zodResolver(schema), defaultValues: { fullName: '', email: '', password: '' } });

  useEffect(() => { if (session) navigate(homeForRoles(session.roles), { replace: true }); }, [session, navigate]);

  async function submit(values) {
    setBusy(true); setError('');
    try {
      if (registering) {
        if (!values.fullName?.trim() || values.fullName.trim().length < 2) {
          setFieldError('fullName', { message: 'Enter your full name.' }); setBusy(false); return;
        }
        await register(values.fullName.trim(), values.email, values.password);
      } else {
        await signIn(values.email, values.password);
      }
    } catch (cause) {
      if (cause.code !== 'auth/invalid-credential') setError(friendlyAuthError(cause));
      else setError('Email or password was not recognized.');
    } finally { setBusy(false); }
  }

  async function google() {
    setBusy(true); setError('');
    try { await signInWithGoogle(); } catch (cause) { setError(friendlyAuthError(cause)); } finally { setBusy(false); }
  }

  return (
    <main className="login-shell">
      <section className="login-story">
        <div className="flex items-center gap-3"><span className="brand-mark"><Stethoscope size={21} /></span><span className="text-sm font-bold tracking-wide text-white">CARE<span className="text-teal-300">FLOW</span></span></div>
        <div className="story-copy"><div className="story-kicker"><Activity size={15} /> CLINIC OPERATIONS, CONNECTED</div><h1>More time for<br /><span>better care.</span></h1><p>Bring appointments, patient records and your team into one calm, connected workspace.</p></div>
        <div className="story-footer"><ShieldCheck size={16} /> Secure access powered by Firebase Authentication</div>
        <div className="story-orb story-orb-one" /><div className="story-orb story-orb-two" />
      </section>
      <section className="login-panel-wrap">
        <div className="login-panel">
          <div className="mobile-brand"><span className="brand-mark"><Stethoscope size={19} /></span><span className="text-sm font-bold tracking-wide">CAREFLOW</span></div>
          <p className="eyebrow">{registering ? 'GET STARTED' : 'WELCOME BACK'}</p>
          <h2>{registering ? 'Create your account' : 'Sign in to your workspace'}</h2>
          <p className="login-caption">{registering ? 'Your secure patient account starts here.' : 'Your workspace is ready when you are.'}</p>
          {(error || sessionError) && <div role="alert" className="form-alert">{error || sessionError}</div>}
          <form className="mt-7 space-y-4" onSubmit={handleSubmit(submit)} noValidate>
            {registering && <Field label="Full name" type="text" autoComplete="name" registration={field('fullName')} error={errors.fullName?.message} placeholder="Your name" />}
            <Field label="Email address" type="email" autoComplete="email" registration={field('email')} error={errors.email?.message} placeholder="you@example.com" />
            <Field label="Password" type="password" autoComplete={registering ? 'new-password' : 'current-password'} registration={field('password')} error={errors.password?.message} placeholder="At least 8 characters" />
            <button className="button-primary w-full" type="submit" disabled={busy || !firebaseConfigured}>{busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}<ArrowRight size={16} /></button>
          </form>
          <div className="divider"><span>OR CONTINUE WITH</span></div>
          <button className="button-google w-full" onClick={google} disabled={busy || !firebaseConfigured}><Globe size={17} /> Google</button>
          {!firebaseConfigured && <p className="mt-4 rounded-xl bg-amber-50 px-3.5 py-3 text-xs leading-5 text-amber-800">Add Firebase web credentials to <code>.env</code> to enable sign-in.</p>}
          <p className="switch-auth">{registering ? 'Already have an account?' : 'New to Careflow?'} <button type="button" onClick={() => { setRegistering(!registering); setError(''); }}> {registering ? 'Sign in' : 'Create an account'}</button></p>
          <p className="privacy-note">For authorized patients and care teams. Keep your sign-in details private.</p>
        </div>
      </section>
    </main>
  );
}

function Field({ label, type, autoComplete, registration, error, placeholder }) {
  return <label className="block text-sm font-medium text-slate-700">{label}<input className={`form-input ${error ? 'form-input-error' : ''}`} type={type} autoComplete={autoComplete} placeholder={placeholder} {...registration} />{error && <span className="mt-1 block text-xs font-normal text-rose-600">{error}</span>}</label>;
}

function friendlyAuthError(error) {
  const messages = {
    'auth/email-already-in-use': 'An account already uses this email. Try signing in.',
    'auth/weak-password': 'Choose a stronger password with at least 8 characters.',
    'auth/popup-closed-by-user': 'Google sign-in was closed before it finished.',
    'auth/too-many-requests': 'Too many attempts. Wait a little and try again.',
    'auth/unauthorized-domain': 'Add this web address to the authorized domains in Firebase.',
  };
  return messages[error.code] || error.response?.data?.error || 'Sign-in could not be completed. Check your connection and try again.';
}
