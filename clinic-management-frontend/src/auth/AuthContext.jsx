import { createContext, useContext, useEffect, useRef, useState } from 'react';
import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  updateProfile,
} from 'firebase/auth';
import { auth, firebaseConfigured, googleProvider } from './firebase.js';
import { api } from '../services/api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState('');
  const requestVersion = useRef(0);
  const authAction = useRef(false);

  async function loadSession(firebaseUser, fullName = '') {
    const version = ++requestVersion.current;
    try {
      await api.post('/auth/register', { fullName: fullName || firebaseUser.displayName || '' });
      const { data } = await api.get('/auth/me');
      if (version === requestVersion.current) { setSession(data); setSessionError(''); }
    } catch (error) {
      if (version === requestVersion.current) setSessionError(error.response?.data?.error || 'Could not load your clinic account.');
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }

  async function runAuthAction(action, fullName = '') {
    authAction.current = true;
    setLoading(true);
    setSessionError('');
    try {
      const credential = await action();
      if (fullName) await updateProfile(credential.user, { displayName: fullName });
      setUser(credential.user);
      await loadSession(credential.user, fullName);
      return credential;
    } catch (error) {
      if (auth?.currentUser) {
        setUser(auth.currentUser);
        await loadSession(auth.currentUser);
      } else {
        setLoading(false);
      }
      throw error;
    } finally {
      authAction.current = false;
    }
  }

  useEffect(() => {
    if (!auth) { setLoading(false); return undefined; }
    return onAuthStateChanged(auth, (firebaseUser) => {
      if (authAction.current && firebaseUser) { setUser(firebaseUser); return; }
      ++requestVersion.current;
      setUser(firebaseUser);
      setSession(null);
      setSessionError('');
      if (!firebaseUser) setLoading(false);
      else if (authAction.current) setLoading(true);
      else { setLoading(true); loadSession(firebaseUser); }
    });
  }, []);

  const value = {
    user, session, loading, sessionError, firebaseConfigured,
    signIn: (email, password) => runAuthAction(() => signInWithEmailAndPassword(auth, email, password)),
    register: (fullName, email, password) => runAuthAction(
      () => createUserWithEmailAndPassword(auth, email, password), fullName),
    signInWithGoogle: () => runAuthAction(() => signInWithPopup(auth, googleProvider)),
    signOut: () => signOut(auth),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
