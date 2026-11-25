CREATE TABLE doctor_schedule_exceptions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id uuid NOT NULL,
    doctor_profile_id uuid NOT NULL,
    exception_date date NOT NULL,
    reason varchar(240),
    active boolean NOT NULL DEFAULT true,
    created_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (clinic_id, doctor_profile_id) REFERENCES doctor_profiles(clinic_id, id) ON DELETE RESTRICT,
    UNIQUE (clinic_id, doctor_profile_id, exception_date)
);
CREATE INDEX idx_schedule_exceptions_active ON doctor_schedule_exceptions(clinic_id, doctor_profile_id, exception_date) WHERE active;
