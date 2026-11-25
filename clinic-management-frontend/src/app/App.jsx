import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from '../auth/ProtectedRoute.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import LoginPage from '../pages/LoginPage.jsx';
import LoadingScreen from '../components/LoadingScreen.jsx';
import { homeForRoles } from '../utils/roles.js';

const PatientAppointmentsPage = lazy(() => import('../pages/PatientAppointmentsPage.jsx'));
const PatientDashboardPage = lazy(() => import('../pages/PatientDashboardPage.jsx'));
const PatientRecordsPage = lazy(() => import('../pages/PatientRecordsPage.jsx'));
const PatientProfilePage = lazy(() => import('../pages/PatientProfilePage.jsx'));
const DoctorAppointmentsPage = lazy(() => import('../pages/DoctorAppointmentsPage.jsx'));
const DoctorDashboardPage = lazy(() => import('../pages/DoctorDashboardPage.jsx'));
const DoctorSchedulePage = lazy(() => import('../pages/DoctorSchedulePage.jsx'));
const DoctorConsultationPage = lazy(() => import('../pages/DoctorConsultationPage.jsx'));
const DoctorStaffPage = lazy(() => import('../pages/DoctorStaffPage.jsx'));
const DoctorPatientsPage = lazy(() => import('../pages/DoctorPatientsPage.jsx'));
const DoctorSettingsPage = lazy(() => import('../pages/DoctorSettingsPage.jsx'));
const StaffDashboardPage = lazy(() => import('../pages/StaffDashboardPage.jsx'));
const AcceptStaffInvitationPage = lazy(() => import('../pages/AcceptStaffInvitationPage.jsx'));
const BillingPage = lazy(() => import('../pages/BillingPage.jsx'));
const NotificationsPage = lazy(() => import('../pages/NotificationsPage.jsx'));

function HomeRedirect() {
  const { user, session, loading } = useAuth();
  if (loading) return <LoadingScreen />;
  return <Navigate to={user && session ? homeForRoles(session.roles) : '/login'} replace />;
}

export default function App() {
  return <Suspense fallback={<LoadingScreen />}><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/staff/accept-invitation" element={<AcceptStaffInvitationPage />} />
    <Route element={<ProtectedRoute />}>
      <Route index element={<HomeRedirect />} />
      <Route path="/notifications" element={<NotificationsPage />} />
      <Route element={<ProtectedRoute allowedRoles={['PATIENT']} />}>
        <Route path="/patient" element={<PatientDashboardPage />} />
        <Route path="/patient/appointments" element={<PatientAppointmentsPage />} />
        <Route path="/patient/records" element={<PatientRecordsPage />} />
        <Route path="/patient/profile" element={<PatientProfilePage />} />
        <Route path="/patient/billing" element={<BillingPage role="PATIENT" />} />
      </Route>
      <Route element={<ProtectedRoute allowedRoles={['DOCTOR']} />}>
        <Route path="/doctor" element={<DoctorDashboardPage />} />
        <Route path="/doctor/appointments" element={<DoctorAppointmentsPage />} />
        <Route path="/doctor/appointments/:appointmentId/consultation" element={<DoctorConsultationPage />} />
        <Route path="/doctor/patients" element={<DoctorPatientsPage />} />
        <Route path="/doctor/patients/:clinicPatientId" element={<DoctorPatientsPage />} />
        <Route path="/doctor/schedule" element={<DoctorSchedulePage />} />
        <Route path="/doctor/staff" element={<DoctorStaffPage />} />
        <Route path="/doctor/settings" element={<DoctorSettingsPage />} />
        <Route path="/doctor/billing" element={<BillingPage role="DOCTOR" />} />
      </Route>
      <Route element={<ProtectedRoute allowedRoles={['STAFF']} />}>
        <Route path="/staff" element={<StaffDashboardPage />} />
        <Route path="/staff/queue" element={<StaffDashboardPage />} />
        <Route path="/staff/billing" element={<BillingPage role="STAFF" />} />
      </Route>
    </Route>
    <Route path="*" element={<HomeRedirect />} />
  </Routes></Suspense>;
}
