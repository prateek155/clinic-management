export const ROLE_PATHS = { DOCTOR: '/doctor', STAFF: '/staff', PATIENT: '/patient' };
const ROLE_ORDER = ['DOCTOR', 'STAFF', 'PATIENT'];

export function homeForRoles(roles = []) {
  const role = ROLE_ORDER.find((candidate) => roles.includes(candidate));
  return ROLE_PATHS[role] || '/login';
}
