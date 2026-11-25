import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from '../auth/ProtectedRoute.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import LoginPage from '../pages/LoginPage.jsx';
import DashboardPage from '../pages/DashboardPage.jsx';
import LoadingScreen from '../components/LoadingScreen.jsx';
import { homeForRoles } from '../utils/roles.js';

function HomeRedirect() {
  const { user, session, loading } = useAuth();
  if (loading) return <LoadingScreen />;
  return <Navigate to={user && session ? homeForRoles(session.roles) : '/login'} replace />;
}

export default function App() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<ProtectedRoute />}>
      <Route index element={<HomeRedirect />} />
      <Route element={<ProtectedRoute allowedRoles={['PATIENT']} />}><Route path="/patient" element={<DashboardPage role="PATIENT" />} /></Route>
      <Route element={<ProtectedRoute allowedRoles={['DOCTOR']} />}><Route path="/doctor" element={<DashboardPage role="DOCTOR" />} /></Route>
      <Route element={<ProtectedRoute allowedRoles={['STAFF']} />}><Route path="/staff" element={<DashboardPage role="STAFF" />} /></Route>
    </Route>
    <Route path="*" element={<HomeRedirect />} />
  </Routes>;
}
