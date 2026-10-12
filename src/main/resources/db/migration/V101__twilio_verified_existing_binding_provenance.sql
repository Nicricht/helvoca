-- V101: truthful, explicitly verified Twilio numbers that were NOT purchased by Helvoca.
-- Schema preparation ONLY. There is intentionally no INSERT, UPDATE, backfill or activation.
-- V100 MANAGED_PURCHASE rows and all NULL-provenance legacy rows retain their meaning.

ALTER TABLE phone_number DROP CONSTRAINT chk_phone_twilio_binding_source;
ALTER TABLE phone_number
    ADD CONSTRAINT chk_phone_twilio_binding_source
    CHECK (twilio_binding_source IS NULL
           OR twilio_binding_source IN ('MANAGED_PURCHASE', 'VERIFIED_EXISTING'));

ALTER TABLE phone_number DROP CONSTRAINT chk_phone_twilio_binding_account;
ALTER TABLE phone_number
    ADD CONSTRAINT chk_phone_twilio_binding_account
    CHECK (
        CASE
            WHEN twilio_binding_source IS NULL
                THEN twilio_account_sid IS NULL
            WHEN twilio_binding_source = 'MANAGED_PURCHASE'
                THEN twilio_account_sid IS NOT NULL
                     AND twilio_account_sid ~ '^AC[0-9a-fA-F]{32}$'
                     AND external_id IS NOT NULL
                     AND external_id ~ '^PN[0-9a-fA-F]{32}$'
            WHEN twilio_binding_source = 'VERIFIED_EXISTING'
                THEN provider = 'TWILIO'
                     AND phone_number ~ '^\+[1-9][0-9]{7,14}$'
                     AND twilio_account_sid IS NOT NULL
                     AND twilio_account_sid ~ '^AC[0-9a-fA-F]{32}$'
                     AND external_id IS NOT NULL
                     AND external_id ~ '^PN[0-9a-fA-F]{32}$'
                     AND voice_verified_at IS NOT NULL
            ELSE FALSE
        END
    );

-- Preserve the original V100 managed-only index; add a common uniqueness
-- fence over BOTH certified sources. The original (provider,external_id)
-- unique index from V4 remains untouched, including for legacy rows.
CREATE UNIQUE INDEX uq_phone_twilio_certified_external
    ON phone_number (external_id)
    WHERE twilio_binding_source IN ('MANAGED_PURCHASE', 'VERIFIED_EXISTING');

-- RLS isolates tenant rows, but a tenant role that can issue arbitrary SQL
-- must NOT be allowed to fabricate carrier evidence on its own tenant row.
-- This is a second DB-level fail-closed fence, not the later admin workflow.
CREATE FUNCTION public.helvoca_verified_existing_write_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.twilio_binding_source = 'VERIFIED_EXISTING'
           AND current_user <> 'helvoca_system' THEN
            RAISE EXCEPTION 'verified-existing binding requires platform system scope'
                USING ERRCODE = '42501';
        END IF;
    ELSIF TG_OP = 'UPDATE' THEN
        IF (OLD.twilio_binding_source = 'VERIFIED_EXISTING'
            OR NEW.twilio_binding_source = 'VERIFIED_EXISTING')
           AND current_user <> 'helvoca_system' THEN
            RAISE EXCEPTION 'verified-existing binding requires platform system scope'
                USING ERRCODE = '42501';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

REVOKE ALL ON FUNCTION public.helvoca_verified_existing_write_guard() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.helvoca_verified_existing_write_guard()
    TO helvoca_runtime, helvoca_system;

CREATE TRIGGER trg_phone_verified_existing_write_guard
    BEFORE INSERT OR UPDATE ON phone_number
    FOR EACH ROW EXECUTE FUNCTION public.helvoca_verified_existing_write_guard();

COMMENT ON COLUMN phone_number.twilio_binding_source IS
    'NULL=unverified legacy; MANAGED_PURCHASE=managed carrier purchase; VERIFIED_EXISTING=separately verified existing number. Never infer provenance from SID alone.';
COMMENT ON FUNCTION public.helvoca_verified_existing_write_guard() IS
    'Only helvoca_system may write an existing-number verified binding. Administrative approval and fresh carrier proof are separate application requirements.';
