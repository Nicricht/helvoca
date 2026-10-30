CREATE TABLE public.twilio_single_operator_demo_enrollment (
    change_id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    phone_number_id UUID NOT NULL,
    destination_e164 VARCHAR(30) NOT NULL
        CHECK (destination_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    owner_account_sid VARCHAR(34) NOT NULL
        CHECK (owner_account_sid ~ '^AC[0-9a-fA-F]{32}$'),
    actor_id UUID NOT NULL REFERENCES public.app_user(id),
    state VARCHAR(8) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'APPLIED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (failed_attempts BETWEEN 0 AND 3),
    phone_sid VARCHAR(34)
        CHECK (phone_sid IS NULL OR phone_sid ~ '^PN[0-9a-fA-F]{32}$'),
    carrier_verified_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT fk_single_operator_phone
        FOREIGN KEY (phone_number_id,business_id)
        REFERENCES public.phone_number (id,business_id),
    CONSTRAINT chk_single_operator_expiry CHECK (
        expires_at > requested_at
        AND expires_at <= requested_at + INTERVAL '15 minutes'),
    CONSTRAINT chk_single_operator_state CHECK (
        (state='PENDING' AND phone_sid IS NULL
            AND carrier_verified_at IS NULL AND confirmed_at IS NULL)
        OR (state='APPLIED' AND phone_sid IS NOT NULL
            AND carrier_verified_at IS NOT NULL AND confirmed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_twilio_single_operator_applied_phone
    ON public.twilio_single_operator_demo_enrollment(phone_number_id)
    WHERE state='APPLIED';
CREATE UNIQUE INDEX uq_twilio_single_operator_applied_sid
    ON public.twilio_single_operator_demo_enrollment(phone_sid)
    WHERE state='APPLIED';

CREATE FUNCTION public.helvoca_single_operator_enrollment_guard()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY INVOKER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'single operator enrollment immutable' USING ERRCODE='42501';
    END IF;
    IF (OLD.change_id,OLD.business_id,OLD.phone_number_id,
        OLD.destination_e164,OLD.owner_account_sid,OLD.actor_id,
        OLD.requested_at,OLD.expires_at)
        IS DISTINCT FROM
       (NEW.change_id,NEW.business_id,NEW.phone_number_id,
        NEW.destination_e164,NEW.owner_account_sid,NEW.actor_id,
        NEW.requested_at,NEW.expires_at) THEN
        RAISE EXCEPTION 'single operator enrollment identity immutable' USING ERRCODE='42501';
    END IF;
    IF OLD.state <> 'PENDING' OR (
        NOT (
            NEW.state='PENDING'
            AND NEW.failed_attempts=OLD.failed_attempts+1
            AND NEW.phone_sid IS NOT DISTINCT FROM OLD.phone_sid
            AND NEW.carrier_verified_at IS NOT DISTINCT FROM OLD.carrier_verified_at
            AND NEW.confirmed_at IS NOT DISTINCT FROM OLD.confirmed_at
        )
        AND NOT (
            NEW.state='APPLIED'
            AND NEW.failed_attempts=OLD.failed_attempts
            AND NEW.phone_sid IS NOT NULL
            AND NEW.carrier_verified_at IS NOT NULL
            AND NEW.confirmed_at IS NOT NULL
        )) THEN
        RAISE EXCEPTION 'invalid single operator enrollment transition' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END
$$;
REVOKE ALL ON FUNCTION public.helvoca_single_operator_enrollment_guard() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.helvoca_single_operator_enrollment_guard() TO helvoca_system;
CREATE TRIGGER trg_single_operator_enrollment_guard
    BEFORE UPDATE OR DELETE ON public.twilio_single_operator_demo_enrollment
    FOR EACH ROW EXECUTE FUNCTION public.helvoca_single_operator_enrollment_guard();

CREATE TABLE public.twilio_single_operator_demo_audit (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL REFERENCES public.twilio_single_operator_demo_enrollment(change_id),
    actor_id UUID NOT NULL REFERENCES public.app_user(id),
    action VARCHAR(12) NOT NULL CHECK (action IN ('REQUESTED','DENIED','CONFIRMED')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

REVOKE ALL ON TABLE public.twilio_single_operator_demo_enrollment
    FROM PUBLIC,helvoca_runtime,helvoca_system;
GRANT SELECT,INSERT,UPDATE ON public.twilio_single_operator_demo_enrollment TO helvoca_system;
REVOKE ALL ON TABLE public.twilio_single_operator_demo_audit
    FROM PUBLIC,helvoca_runtime,helvoca_system;
GRANT SELECT,INSERT ON public.twilio_single_operator_demo_audit TO helvoca_system;
ALTER TABLE public.twilio_single_operator_demo_enrollment ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_single_operator_demo_enrollment FORCE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_single_operator_demo_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_single_operator_demo_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY helvoca_system_single_operator_enrollment
    ON public.twilio_single_operator_demo_enrollment
    FOR ALL TO helvoca_system USING (true) WITH CHECK (true);
CREATE POLICY helvoca_system_single_operator_audit
    ON public.twilio_single_operator_demo_audit
    FOR ALL TO helvoca_system USING (true) WITH CHECK (true);

COMMENT ON TABLE public.twilio_single_operator_demo_enrollment IS
    'Default-off isolated demo attestation by signed platform admin with password reauthentication and fresh independent carrier GET. Not an ordinary customer enrollment.';
),
    owner_account_sid VARCHAR(34) NOT NULL
        CHECK (owner_account_sid ~ '^AC[0-9a-fA-F]{32}$'),
    actor_id UUID NOT NULL REFERENCES public.app_user(id),
    state VARCHAR(8) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'APPLIED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (failed_attempts BETWEEN 0 AND 3),
    phone_sid VARCHAR(34)
        CHECK (phone_sid IS NULL OR phone_sid ~ '^PN[0-9a-fA-F]{32}$'),
    carrier_verified_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT fk_single_operator_phone
        FOREIGN KEY (phone_number_id,business_id)
        REFERENCES public.phone_number (id,business_id),
    CONSTRAINT chk_single_operator_expiry CHECK (
        expires_at > requested_at
        AND expires_at <= requested_at + INTERVAL '15 minutes'),
    CONSTRAINT chk_single_operator_state CHECK (
        (state='PENDING' AND phone_sid IS NULL
            AND carrier_verified_at IS NULL AND confirmed_at IS NULL)
        OR (state='APPLIED' AND phone_sid IS NOT NULL
            AND carrier_verified_at IS NOT NULL AND confirmed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_twilio_single_operator_applied_phone
    ON public.twilio_single_operator_demo_enrollment(phone_number_id)
    WHERE state='APPLIED';
CREATE UNIQUE INDEX uq_twilio_single_operator_applied_sid
    ON public.twilio_single_operator_demo_enrollment(phone_sid)
    WHERE state='APPLIED';

CREATE FUNCTION public.helvoca_single_operator_enrollment_guard()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY INVOKER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'single operator enrollment immutable' USING ERRCODE='42501';
    END IF;
    IF (OLD.change_id,OLD.business_id,OLD.phone_number_id,
        OLD.destination_e164,OLD.owner_account_sid,OLD.actor_id,
        OLD.requested_at,OLD.expires_at)
        IS DISTINCT FROM
       (NEW.change_id,NEW.business_id,NEW.phone_number_id,
        NEW.destination_e164,NEW.owner_account_sid,NEW.actor_id,
        NEW.requested_at,NEW.expires_at) THEN
        RAISE EXCEPTION 'single operator enrollment identity immutable' USING ERRCODE='42501';
    END IF;
    IF OLD.state <> 'PENDING' OR (
        NOT (
            NEW.state='PENDING'
            AND NEW.failed_attempts=OLD.failed_attempts+1
            AND NEW.phone_sid IS NOT DISTINCT FROM OLD.phone_sid
            AND NEW.carrier_verified_at IS NOT DISTINCT FROM OLD.carrier_verified_at
            AND NEW.confirmed_at IS NOT DISTINCT FROM OLD.confirmed_at
        )
        AND NOT (
            NEW.state='APPLIED'
            AND NEW.failed_attempts=OLD.failed_attempts
            AND NEW.phone_sid IS NOT NULL
            AND NEW.carrier_verified_at IS NOT NULL
            AND NEW.confirmed_at IS NOT NULL
        )) THEN
        RAISE EXCEPTION 'invalid single operator enrollment transition' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END
$$;
REVOKE ALL ON FUNCTION public.helvoca_single_operator_enrollment_guard() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.helvoca_single_operator_enrollment_guard() TO helvoca_system;
CREATE TRIGGER trg_single_operator_enrollment_guard
    BEFORE UPDATE OR DELETE ON public.twilio_single_operator_demo_enrollment
    FOR EACH ROW EXECUTE FUNCTION public.helvoca_single_operator_enrollment_guard();

CREATE TABLE public.twilio_single_operator_demo_audit (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL REFERENCES public.twilio_single_operator_demo_enrollment(change_id),
    actor_id UUID NOT NULL REFERENCES public.app_user(id),
    action VARCHAR(12) NOT NULL CHECK (action IN ('REQUESTED','DENIED','CONFIRMED')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

REVOKE ALL ON TABLE public.twilio_single_operator_demo_enrollment
    FROM PUBLIC,helvoca_runtime,helvoca_system;
GRANT SELECT,INSERT,UPDATE ON public.twilio_single_operator_demo_enrollment TO helvoca_system;
REVOKE ALL ON TABLE public.twilio_single_operator_demo_audit
    FROM PUBLIC,helvoca_runtime,helvoca_system;
GRANT SELECT,INSERT ON public.twilio_single_operator_demo_audit TO helvoca_system;
ALTER TABLE public.twilio_single_operator_demo_enrollment ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_single_operator_demo_enrollment FORCE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_single_operator_demo_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_single_operator_demo_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY helvoca_system_single_operator_enrollment
    ON public.twilio_single_operator_demo_enrollment
    FOR ALL TO helvoca_system USING (true) WITH CHECK (true);
CREATE POLICY helvoca_system_single_operator_audit
    ON public.twilio_single_operator_demo_audit
    FOR ALL TO helvoca_system USING (true) WITH CHECK (true);

COMMENT ON TABLE public.twilio_single_operator_demo_enrollment IS
    'Default-off isolated demo attestation by signed platform admin with password reauthentication and fresh independent carrier GET. Not an ordinary customer enrollment.';
