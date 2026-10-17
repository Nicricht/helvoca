-- V103: two-human platform approval + immutable audit evidence for V102.
-- No enrollment/backfill/activation performed by this migration.
CREATE TABLE public.twilio_existing_enrollment_request (
    change_id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    phone_number_id UUID NOT NULL,
    destination_e164 VARCHAR(30) NOT NULL,
    owner_account_sid VARCHAR(34) NOT NULL,
    requester_id UUID NOT NULL REFERENCES public.app_user(id),
    approver_id UUID REFERENCES public.app_user(id),
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    CONSTRAINT fk_twilio_existing_request_phone
        FOREIGN KEY (phone_number_id, business_id)
        REFERENCES public.phone_number (id, business_id),
    CONSTRAINT chk_twilio_existing_request_e164
        CHECK (destination_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT chk_twilio_existing_request_owner
        CHECK (owner_account_sid ~ '^AC[0-9a-fA-F]{32}$'),
    CONSTRAINT chk_twilio_existing_request_separation
        CHECK (approver_id IS NULL OR approver_id <> requester_id),
    CONSTRAINT chk_twilio_existing_request_time
        CHECK (expires_at > requested_at AND expires_at <= requested_at + INTERVAL '15 minutes'),
    CONSTRAINT chk_twilio_existing_request_state
        CHECK (state IN ('PENDING','APPROVED','APPLIED')
            AND (CASE
                WHEN state = 'PENDING' THEN approver_id IS NULL AND approved_at IS NULL AND executed_at IS NULL
                WHEN state = 'APPROVED' THEN approver_id IS NOT NULL AND approved_at IS NOT NULL AND executed_at IS NULL
                WHEN state = 'APPLIED' THEN approver_id IS NOT NULL AND approved_at IS NOT NULL AND executed_at IS NOT NULL
                ELSE FALSE END))
);
CREATE FUNCTION public.helvoca_twilio_enrollment_request_immutable_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $$
BEGIN
    IF (OLD.change_id, OLD.business_id, OLD.phone_number_id, OLD.destination_e164,
        OLD.owner_account_sid, OLD.requester_id, OLD.requested_at, OLD.expires_at)
       IS DISTINCT FROM
       (NEW.change_id, NEW.business_id, NEW.phone_number_id, NEW.destination_e164,
        NEW.owner_account_sid, NEW.requester_id, NEW.requested_at, NEW.expires_at)
       OR NOT (
           (OLD.state = 'PENDING' AND NEW.state = 'APPROVED')
           OR (OLD.state = 'APPROVED' AND NEW.state = 'APPLIED'
               AND OLD.approver_id IS NOT DISTINCT FROM NEW.approver_id
               AND OLD.approved_at IS NOT DISTINCT FROM NEW.approved_at)
       ) THEN
        RAISE EXCEPTION 'invalid platform enrollment transition' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END
$$;
REVOKE ALL ON FUNCTION public.helvoca_twilio_enrollment_request_immutable_guard() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.helvoca_twilio_enrollment_request_immutable_guard()
    TO helvoca_system;
CREATE TRIGGER trg_twilio_existing_request_immutable_guard
    BEFORE UPDATE ON public.twilio_existing_enrollment_request
    FOR EACH ROW EXECUTE FUNCTION public.helvoca_twilio_enrollment_request_immutable_guard();

CREATE INDEX idx_twilio_existing_request_phone
    ON public.twilio_existing_enrollment_request(phone_number_id, state);

CREATE TABLE public.twilio_existing_enrollment_audit (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL REFERENCES public.twilio_existing_enrollment_request(change_id),
    actor_id UUID NOT NULL REFERENCES public.app_user(id),
    action VARCHAR(32) NOT NULL
        CHECK (action IN ('REQUESTED','APPROVED','EXECUTED','EXECUTION_BLOCKED')),
    outcome VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_twilio_existing_audit_change
    ON public.twilio_existing_enrollment_audit(change_id, occurred_at);

-- Explicitly remove privileges from Flyway's V40 default grants.
REVOKE ALL ON TABLE public.twilio_existing_enrollment_request FROM PUBLIC, helvoca_runtime, helvoca_system;
REVOKE ALL ON TABLE public.twilio_existing_enrollment_audit FROM PUBLIC, helvoca_runtime, helvoca_system;
GRANT SELECT, INSERT, UPDATE ON TABLE public.twilio_existing_enrollment_request TO helvoca_system;
GRANT SELECT, INSERT ON TABLE public.twilio_existing_enrollment_audit TO helvoca_system;

ALTER TABLE public.twilio_existing_enrollment_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_existing_enrollment_request FORCE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_existing_enrollment_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_existing_enrollment_audit FORCE ROW LEVEL SECURITY;

CREATE POLICY helvoca_system_existing_request ON public.twilio_existing_enrollment_request
    FOR ALL TO helvoca_system USING (true) WITH CHECK (true);
CREATE POLICY helvoca_system_existing_audit ON public.twilio_existing_enrollment_audit
    FOR ALL TO helvoca_system USING (true) WITH CHECK (true);

COMMENT ON TABLE public.twilio_existing_enrollment_request IS
    'Trusted platform operator request; 15-minute approval/execute window. System-only.';
COMMENT ON TABLE public.twilio_existing_enrollment_audit IS
    'Append-only immutable actor/action/outcome events. No tenant access. No Twilio secret/SID.';
