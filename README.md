# Careflow Clinic Management System

Phase 1 establishes the application foundation: React/Vite sign-in, Firebase-backed API authentication, patient account provisioning, optional doctor-owner bootstrap, PostgreSQL/Flyway, and role-protected workspace shells. The full relational schema is included now; scheduling, consultations, staff invitations, billing, storage, and notifications are roadmap work and are not exposed as working flows yet.

## Prerequisites

- Java 21
- Maven 3.6.3 or later
- Node.js 20.19 or later and npm
- A Firebase project with Email/Password and Google sign-in enabled
- A Neon project/database, or Docker Compose if you prefer local PostgreSQL

You do not need PostgreSQL installed locally when using Neon.

## Configure environment variables

Copy the root template and fill its backend values:

```powershell
Set-Location 'C:\Users\ishu0\OneDrive\Desktop\clinic'
Copy-Item .env.example .env
notepad .env
```

### Neon PostgreSQL

In Neon Console, open **Connect**, select your branch, database, and role, then copy the connection details. Put the JDBC connection URL in `DB_URL`, and the database role and password in `DB_USERNAME` and `DB_PASSWORD`. Keep SSL enabled (`sslmode=require`). If Neon shows a `postgresql://user:password@host/database?...` URI instead of a JDBC URL, use `jdbc:postgresql://host/database?...` for `DB_URL` and put the user and password in their separate fields. Do not include the username or password in `DB_URL`.

The backend runs Flyway migrations on startup, so the database schema is created automatically. Neon’s connection details are available from its [Connect documentation](https://neon.com/docs/connect/connect-from-any-app).

### Firebase

1. In Firebase Console, create a Web App and enable **Email/Password** and **Google** under Authentication providers. Add `localhost` to authorized domains for local development.
2. Copy `clinic-management-frontend/.env.example` to `clinic-management-frontend/.env`. Fill `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, and `VITE_FIREBASE_APP_ID` from Firebase Console → Project settings → General → Your apps → Web app. Keep `VITE_API_URL` as `http://localhost:8080/api/v1` for local development. These web settings are used by the browser and are not Firebase Admin credentials.
3. For the backend, download a Firebase Admin service-account JSON and store it outside this repository, for example `C:\secure\firebase-admin.json`. Set `GOOGLE_APPLICATION_CREDENTIALS` in the root `.env` to that file path, and set `FIREBASE_PROJECT_ID` to the same project ID as the web app. Never paste the JSON contents into an `.env` file or commit the JSON.
4. To create a doctor owner, first create that user's email/password or Google account in Firebase Authentication. Then enable the optional bootstrap settings in `.env` (`BOOTSTRAP_CLINIC_ENABLED=true`, `BOOTSTRAP_OWNER_EMAIL`, and optionally `BOOTSTRAP_CLINIC_NAME`). Leave bootstrap disabled for patient-only setup.

### Environment variable locations

| Values | File | Notes |
|---|---|---|
| Neon JDBC URL, role, and password: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Root `.env` | Use the Neon database role/password and require SSL. |
| Firebase Admin: `FIREBASE_ENABLED`, `FIREBASE_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS` | Root `.env` | The last value is a path to the JSON key file, not the JSON contents. |
| Server and local settings: `SERVER_PORT`, `OPENAPI_ENABLED`, `CORS_ALLOWED_ORIGINS` | Root `.env` | Defaults are suitable for local development. |
| Optional doctor bootstrap: `BOOTSTRAP_CLINIC_ENABLED`, `BOOTSTRAP_OWNER_EMAIL`, `BOOTSTRAP_CLINIC_NAME` | Root `.env` | Only enable after creating the owner identity in Firebase. |
| Firebase browser config and API URL: `VITE_FIREBASE_*`, `VITE_API_URL` | `clinic-management-frontend/.env` | Never put Neon credentials or Firebase Admin JSON/key in the frontend file. |
| Optional local Docker PostgreSQL password: `POSTGRES_PASSWORD` | Root `.env` | Only needed if you choose Docker Compose instead of Neon. |

The backend imports the root `.env` when it is started from `clinic-management-backend`. Spring Boot supports loading extensionless `.env` files as properties using the `.properties` extension hint ([external configuration docs](https://docs.spring.io/spring-boot/reference/features/external-config.html)).

## Run locally (PowerShell)

Use two PowerShell windows. Install Java 21, Maven 3.6.3+, and Node 20.19+ first. The commands below use Neon, so Docker and a local PostgreSQL installation are not required.

**Window 1 — start the Spring API:**

```powershell
Set-Location 'C:\Users\ishu0\OneDrive\Desktop\clinic'
if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env' }
notepad '.env'
Set-Location 'clinic-management-backend'
mvn spring-boot:run
```

Fill in the root `.env` before starting the API. For patient-only setup, leave `BOOTSTRAP_CLINIC_ENABLED=false`.

**Window 2 — start the React app:**

```powershell
Set-Location 'C:\Users\ishu0\OneDrive\Desktop\clinic'
if (-not (Test-Path 'clinic-management-frontend/.env')) { Copy-Item 'clinic-management-frontend/.env.example' 'clinic-management-frontend/.env' }
notepad 'clinic-management-frontend/.env'
Set-Location 'clinic-management-frontend'
npm install
npm run dev
```

The SPA runs at `http://localhost:5173`; the API runs at `http://localhost:8080`. The public liveness endpoint is `http://localhost:8080/api/v1/health`. In development, Swagger UI is at `http://localhost:8080/swagger-ui.html`; set `OPENAPI_ENABLED=false` in deployments that should not publish API docs.

To use local PostgreSQL with Docker Compose instead, run `docker compose up -d postgres` from the workspace root and set `DB_URL=jdbc:postgresql://localhost:5432/clinic`, `DB_USERNAME=clinic`, and `DB_PASSWORD` to match the optional `POSTGRES_PASSWORD` value.

## Phase 1 API

- `POST /api/v1/auth/register` provisions a patient profile from the verified Firebase UID and email. The request accepts a display name only; it cannot set a role, UID, clinic, or permission.
- `GET /api/v1/auth/me` returns the local user, roles, and active clinic memberships.
- `GET /api/v1/health` returns `{ "status": "ok" }` without authentication.

## Scope and data handling

This foundation is not yet a production-ready medical-record system. Before real patient data is stored, implement and review clinic-scoped endpoint authorization, staff permissions, private object storage, audit events, rate limits, consent and retention policies, database backup/restore, and operational monitoring. No medical report is uploaded or exposed in Phase 1.
