import test from 'node:test';
import assert from 'node:assert/strict';
import { homeForRoles } from './roles.js';

test("selects a user's highest workspace role", () => {
  assert.equal(homeForRoles(['PATIENT', 'DOCTOR']), '/doctor');
});

test('unknown roles return to sign-in', () => {
  assert.equal(homeForRoles([]), '/login');
});
