-- V102: internal, append-only technical claim for verified-existing phone enrollment.
-- This migration changes no existing phone row, never backfills, and grants no tenant access.
CREATE TABLE public.twilio_existing_enrollment_claim (
    change_id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    phone_number_id UUID NOT NULL,
    destination_e164 VARCHAR(30) NOT NULL,
    owner_account_sid VARCHAR(34) NOT NULL,
    phone_sid VARCHAR(34) NOT NULL,
    requester_id UUID NOT NULL,
    approver_id UUID NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_twilio_existing_enrollment_phone
        FOREIGN KEY (phone_number_id, business_id)
        REFERENCES public.phone_number (id, business_id),
    CONSTRAINT uq_twilio_existing_enrollment_phone UNIQUE (phone_number_id),
    CONSTRAINT uq_twilio_existing_enrollment_sid UNIQUE (phone_sid),
    CONSTRAINT chk_twilio_existing_enrollment_two_people CHECK (requester_id <> approver_id),
    CONSTRAINT chk_twilio_existing_enrollment_e164
        CHECK (destination_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT chk_twilio_existing_enrollment_ac
        CHECK (owner_account_sid ~ '^AC[0-9a-fA-F]{32}$'),
    CONSTRAINT chk_twilio_existing_enrollment_pn
        CHECK (phone_sid ~ '^PN[0-9a-fA-F]{32}$')
);

-- Runtime tenant role has no table privilege. Even a future accidental grant
-- must not expose cross-tenant carrier IDs: forced RLS allows only SYSTEM.
REVOKE ALL ON TABLE public.twilio_existing_enrollment_claim FROM PUBLIC, helvoca_runtime, helvoca_system;
GRANT SELECT, INSERT ON TABLE public.twilio_existing_enrollment_claim TO helvoca_system;
ALTER TABLE public.twilio_existing_enrollment_claim ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_existing_enrollment_claim FORCE ROW LEVEL SECURITY;
CREATE POLICY helvoca_system_existing_enrollment_claim
    ON public.twilio_existing_enrollment_claim
    FOR ALL TO helvoca_system
    USING (true) WITH CHECK (true);

COMMENT ON TABLE public.twilio_existing_enrollment_claim IS
    'Internal idempotency/transaction evidence, not human approval audit or live Twilio attestation. No tenant access. Only successful enrollments.';
