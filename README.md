# Careflow Clinic Management System

Careflow is a React/Vite clinic portal backed by Spring Boot, Firebase Authentication, and PostgreSQL. Neon can host PostgreSQL, so a local PostgreSQL installation is optional. The app has patient, doctor, and clinic staff workspaces. Roles come from backend membership records; users cannot grant themselves doctor or staff access from the browser.

## What is implemented

- Patient account and profile editing, family profiles with clinic-specific booking consent, appointment booking, rescheduling, cancellation, and appointment history.
- Doctor dashboard, patient directory and clinic-scoped records, medical history and allergy updates, appointment status management, recurring schedules, breaks, and one-day closures.
- Consultation notes, vitals, follow-up details, draft and finalized prescriptions, and patient medical history views.
- Private medical report uploads/downloads through Firebase Storage using temporary signed links.
- Doctor-managed staff invitations, one-time invitation links, permission assignment and revocation, and staff queue/check-in screens.
- Invoices, line items, payment recording, patient invoice views, and printable invoices.
- In-app notifications and notification preferences.
- Clinic and doctor profile, appointment booking/cancellation rules, and a clinic service catalogue.

Email/SMS/WhatsApp notifications are preference fields only; an external sending provider is not connected. Payments are recorded by clinic staff; there is no payment gateway or card charging flow.

## Prerequisites

- Java 21 and Maven 3.6.3 or later for running the API outside Docker.
- Node.js 20.19 or later and npm for the frontend.
- A Firebase project with Email/Password and Google sign-in enabled.
- A Neon PostgreSQL database, or Docker Compose if using local PostgreSQL.

## Environment setup

Copy the templates and fill them locally. The root `.env` and Firebase Admin JSON are ignored by Git.

```powershell
Set-Location 'C:\Users\ishu0\OneDrive\Desktop\clinic'
Copy-Item .env.example .env
Copy-Item clinic-management-frontend/.env.example clinic-management-frontend/.env
notepad .env
notepad clinic-management-frontend/.env
```

### Neon PostgreSQL

In Neon Console, open **Connect** and select the database, branch, and role. Set `DB_URL` to a JDBC URL such as `jdbc:postgresql://HOST/DB?sslmode=require`, and put the role and password separately in `DB_USERNAME` and `DB_PASSWORD`. Do not include the username/password inside `DB_URL`. Flyway applies the database migrations when the backend starts.

### Firebase Authentication and Admin SDK

1. Enable Email/Password and Google under Firebase Console → Authentication → Sign-in method. Add the deployed frontend domain to Authorized domains.
2. Set `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, and `VITE_FIREBASE_APP_ID` in `clinic-management-frontend/.env` from Firebase Project settings → Your apps. `VITE_API_URL` is the API base URL ending in `/api/v1`.
3. Create a Firebase Admin service-account JSON and keep it out of the repository. Set `FIREBASE_ENABLED=true`, `FIREBASE_PROJECT_ID`, and `GOOGLE_APPLICATION_CREDENTIALS` in the backend environment. The credentials variable must point to a file path; do not paste JSON into an environment variable.
4. Medical report storage additionally needs a Firebase Storage bucket. Set `FIREBASE_STORAGE_BUCKET` to the bucket name without the `gs://` prefix and grant the Admin service account permission to create/read/delete objects in that bucket. Keep the bucket private; the API authorizes access and returns temporary links.

### Create the doctor portal

Create or use the doctor's Firebase account first. For the first clinic owner, configure the backend environment with `BOOTSTRAP_CLINIC_ENABLED=true`, `BOOTSTRAP_OWNER_EMAIL` set to that exact Firebase email, and `BOOTSTRAP_CLINIC_NAME`. Deploy/restart the backend once; its startup bootstrap creates the clinic and doctor profile for that account. Then change `BOOTSTRAP_CLINIC_ENABLED=false` and redeploy/restart. Sign out and sign in again. A user can have both patient and doctor access; the doctor workspace is selected automatically. Staff access is granted separately through **Doctor portal → Staff** and an invitation link.

### Environment variable locations

| Variables | Location | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Backend environment / root `.env` | Neon PostgreSQL connection. |
| `FIREBASE_ENABLED`, `FIREBASE_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS` | Backend environment / root `.env` | Firebase Admin authentication. The credentials value is a path. |
| `FIREBASE_STORAGE_BUCKET` | Backend environment / root `.env` | Private report storage bucket. |
| `BOOTSTRAP_CLINIC_ENABLED`, `BOOTSTRAP_OWNER_EMAIL`, `BOOTSTRAP_CLINIC_NAME` | Backend environment / root `.env` | One-time clinic owner bootstrap. Disable after it runs. |
| `CORS_ALLOWED_ORIGINS`, `OPENAPI_ENABLED`, `SERVER_PORT` | Backend environment / root `.env` | API deployment and local settings. |
| `VITE_API_URL`, `VITE_FIREBASE_*` | `clinic-management-frontend/.env` or frontend build environment | Browser Firebase config and API URL. Never put backend secrets here. |

## Run locally in PowerShell

Use two terminals. Configure Neon and Firebase first. Java 21 and Maven must be installed to run the API this way.

**Terminal 1 — API:**

```powershell
Set-Location 'C:\Users\ishu0\OneDrive\Desktop\clinic\clinic-management-backend'
mvn spring-boot:run
```

**Terminal 2 — frontend:**

```powershell
Set-Location 'C:\Users\ishu0\OneDrive\Desktop\clinic\clinic-management-frontend'
npm install
npm run dev
```

The app runs at `http://localhost:5173`; the API health endpoint is `http://localhost:8080/api/v1/health`. From the repository root, local PostgreSQL can optionally be started with `docker compose up -d postgres`.

## Deploy on Render

Deploy the backend as a **Docker** service using `clinic-management-backend` as its root directory and its included `Dockerfile`. Add the backend variables from the table above in Render. Add the Admin JSON through Render **Secret Files** at `/etc/secrets/firebase-admin.json`, then set `GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-admin.json`. Set `CORS_ALLOWED_ORIGINS` to the exact frontend origin. The backend applies Flyway migrations on startup; V2 adds doctor schedule exceptions.

Deploy the frontend as a **Static Site** with root directory `clinic-management-frontend`, build command `npm ci && npm run build`, and publish directory `dist`. Set `VITE_API_URL` to `https://YOUR-BACKEND.onrender.com/api/v1` and set all four `VITE_FIREBASE_*` values in the frontend build environment. Add a rewrite from `/*` to `/index.html` with status `200` for React Router deep links. After changing build environment variables, trigger a new frontend deploy.

Do not commit `.env`, Firebase Admin JSON, Render secret files, or Neon credentials. If any credential was accidentally committed or shared, rotate it in the provider console.

## Checks

From `clinic-management-frontend`, `npm test` runs the frontend unit tests and `npm run build` creates the production static bundle. The backend targets Java 21; its Dockerfile builds the application using Maven and currently skips backend tests during image creation.

Before using real patient data, have the deployment, authorization rules, backups/restore, and clinic policies reviewed for your operating requirements.
