-- V85: live demo execution evidence and safe PILOT conversion provenance.

ALTER TABLE public.demo_session
    ADD COLUMN operator VARCHAR(180) NOT NULL DEFAULT 'platform',
    ADD COLUMN configuration_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN external_effects_state VARCHAR(30) NOT NULL DEFAULT 'DISARMED',
    ADD COLUMN payment_state VARCHAR(30) NOT NULL DEFAULT 'SANDBOX_ONLY',
    ADD COLUMN converted_pilot_business_id UUID REFERENCES public.business(id) ON DELETE RESTRICT;

ALTER TABLE public.demo_session DROP CONSTRAINT ck_demo_session_state;
ALTER TABLE public.demo_session
    ADD CONSTRAINT ck_demo_session_state
        CHECK (state IN ('PREPARING', 'READY', 'ACTIVE', 'FINISHED', 'FAILED', 'ABORTED'));

ALTER TABLE public.demo_session
    ADD CONSTRAINT uq_demo_session_id_runtime UNIQUE (id, runtime_business_id);

CREATE UNIQUE INDEX uq_demo_session_converted_pilot
    ON public.demo_session(converted_pilot_business_id)
    WHERE converted_pilot_business_id IS NOT NULL;

ALTER TABLE public.call_session
    ADD COLUMN demo_session_id UUID,
    ADD CONSTRAINT fk_call_session_demo_scope
        FOREIGN KEY (demo_session_id, business_id)
        REFERENCES public.demo_session(id, runtime_business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.messaging_conversation
    ADD COLUMN demo_session_id UUID,
    ADD CONSTRAINT fk_messaging_conversation_demo_scope
        FOREIGN KEY (demo_session_id, business_id)
        REFERENCES public.demo_session(id, runtime_business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.business_operation
    ADD COLUMN demo_session_id UUID,
    ADD CONSTRAINT fk_business_operation_demo_scope
        FOREIGN KEY (demo_session_id, business_id)
        REFERENCES public.demo_session(id, runtime_business_id)
        ON DELETE RESTRICT;

CREATE INDEX idx_call_session_business_demo_session_started
    ON public.call_session(business_id, demo_session_id, started_at);
CREATE INDEX idx_messaging_conversation_business_demo_session_opened
    ON public.messaging_conversation(business_id, demo_session_id, opened_at);
CREATE INDEX idx_business_operation_business_demo_session_created
    ON public.business_operation(business_id, demo_session_id, created_at);

CREATE OR REPLACE FUNCTION public.helvoca_assign_demo_session_from_source()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    candidate UUID;
BEGIN
    IF NEW.demo_session_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1
              FROM public.demo_session d
             WHERE d.id = NEW.demo_session_id
               AND d.runtime_business_id = NEW.business_id
               AND d.state = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'invalid active demo session correlation';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.source_reference_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT c.demo_session_id
      INTO candidate
      FROM public.call_session c
     WHERE c.id = NEW.source_reference_id
       AND c.business_id = NEW.business_id;

    IF candidate IS NULL THEN
        SELECT m.demo_session_id
          INTO candidate
          FROM public.messaging_conversation m
         WHERE m.id = NEW.source_reference_id
           AND m.business_id = NEW.business_id;
    END IF;

    IF candidate IS NOT NULL AND EXISTS (
        SELECT 1
          FROM public.demo_session d
         WHERE d.id = candidate
           AND d.runtime_business_id = NEW.business_id
           AND d.state = 'ACTIVE'
    ) THEN
        NEW.demo_session_id := candidate;
    END IF;

    RETURN NEW;
END
$$;

REVOKE ALL ON FUNCTION public.helvoca_assign_demo_session_from_source() FROM PUBLIC;

DROP TRIGGER IF EXISTS trg_business_operation_demo_session ON public.business_operation;
CREATE TRIGGER trg_business_operation_demo_session
BEFORE INSERT OR UPDATE OF source_reference_id, business_id, demo_session_id
ON public.business_operation
FOR EACH ROW
EXECUTE FUNCTION public.helvoca_assign_demo_session_from_source();

COMMENT ON COLUMN public.demo_session.configuration_snapshot_json IS
    'Approved configuration snapshot only; excludes provider credentials, tenant history and provider identities.';
COMMENT ON COLUMN public.demo_session.converted_pilot_business_id IS
    'Fresh PILOT tenant created from approved configuration only. Demo evidence is never copied.';
