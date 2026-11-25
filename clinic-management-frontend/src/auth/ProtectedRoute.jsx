import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext.jsx';
import LoadingScreen from '../components/LoadingScreen.jsx';

export default function ProtectedRoute({ allowedRoles }) {
  const { user, session, loading, sessionError, firebaseConfigured, signOut } = useAuth();
  if (loading) return <LoadingScreen />;
  if (!firebaseConfigured) return <SetupNotice />;
  if (!user) return <Navigate to="/login" replace />;
  if (!session) return <AccountNotice message={sessionError} onSignOut={signOut} />;
  if (allowedRoles && !allowedRoles.some((role) => session.roles?.includes(role))) return <Navigate to="/" replace />;
  return <Outlet />;
}

function SetupNotice() {
  return <main className="grid min-h-screen place-items-center p-6"><section className="panel max-w-lg p-8"><p className="eyebrow">Setup needed</p><h1 className="mt-2 text-2xl font-semibold">Connect your Firebase project</h1><p className="mt-3 text-sm leading-6 text-slate-600">Copy the web app values into <code>clinic-management-frontend/.env</code>, enable Email/Password and Google sign-in in Firebase Authentication, then restart Vite.</p></section></main>;
}

function AccountNotice({ message, onSignOut }) {
  return <main className="grid min-h-screen place-items-center p-6"><section className="panel max-w-lg p-8"><p className="eyebrow">Account access</p><h1 className="mt-2 text-2xl font-semibold">Your clinic account is not ready</h1><p className="mt-3 text-sm leading-6 text-slate-600">{message || 'The API could not find an active account for this Firebase user. Try again after the clinic API is running.'}</p><button className="button-secondary mt-6" onClick={onSignOut}>Sign out</button></section></main>;
}
