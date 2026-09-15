-- V35 Universal Confirmation Engine
-- Channel-neutral confirmation lifecycle layered over universal operations.

ALTER TABLE business_operation DROP CONSTRAINT ck_business_operation_status;
ALTER TABLE business_operation ADD CONSTRAINT ck_business_operation_status
    CHECK (status IN (
        'DRAFT','PROPOSED','AWAITING_CONFIRMATION','CONFIRMED','EXECUTING',
        'COMPLETED','CANCELLED','EXPIRED','FAILED'
    ));

-- V29's append-only event log is authoritative for operation history. Keep its
-- status contract aligned before any new lifecycle state can be persisted, or
-- a valid operation transition would fail while its immutable event is appended.
ALTER TABLE business_operation_event
    DROP CONSTRAINT ck_business_operation_event_status;
ALTER TABLE business_operation_event
    ADD CONSTRAINT ck_business_operation_event_status
        CHECK (status IN (
            'DRAFT','PROPOSED','AWAITING_CONFIRMATION','CONFIRMED','EXECUTING',
            'COMPLETED','CANCELLED','EXPIRED','FAILED'
        ));

ALTER TABLE business_operation_event
    DROP CONSTRAINT ck_business_operation_event_previous_status;
ALTER TABLE business_operation_event
    ADD CONSTRAINT ck_business_operation_event_previous_status
        CHECK (previous_status IS NULL OR previous_status IN (
            'DRAFT','PROPOSED','AWAITING_CONFIRMATION','CONFIRMED','EXECUTING',
            'COMPLETED','CANCELLED','EXPIRED','FAILED'
        ));

CREATE TABLE operation_confirmation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    operation_id UUID NOT NULL,
    operation_revision INTEGER NOT NULL,
    token UUID NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'AWAITING',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_channel VARCHAR(20),
    resolved_source_reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_operation_confirmation_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES business_operation(id, business_id) ON DELETE CASCADE,
    CONSTRAINT uq_operation_confirmation_revision
        UNIQUE (business_id, operation_id, operation_revision),
    CONSTRAINT uq_operation_confirmation_token
        UNIQUE (business_id, token),
    CONSTRAINT ck_operation_confirmation_revision CHECK (operation_revision > 0),
    CONSTRAINT ck_operation_confirmation_state CHECK (
        state IN ('AWAITING','CONSUMED','INVALIDATED','EXPIRED','CANCELLED')
    ),
    CONSTRAINT ck_operation_confirmation_channel CHECK (
        resolved_channel IS NULL OR resolved_channel IN ('VOICE','WHATSAPP','MANUAL','API')
    )
);

CREATE INDEX idx_operation_confirmation_operation
    ON operation_confirmation(business_id, operation_id, operation_revision DESC);
CREATE INDEX idx_operation_confirmation_awaiting
    ON operation_confirmation(business_id, expires_at)
    WHERE state = 'AWAITING';

INSERT INTO operation_confirmation (
    business_id, operation_id, operation_revision, token, state, issued_at, expires_at
)
SELECT business_id, id, revision, confirmation_token, 'AWAITING', NOW(), NOW() + INTERVAL '30 minutes'
FROM business_operation
WHERE status = 'AWAITING_CONFIRMATION' AND confirmation_token IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION sync_operation_confirmation_from_operation()
RETURNS TRIGGER AS $$
DECLARE
    terminal_state VARCHAR(20);
    should_issue BOOLEAN := FALSE;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.confirmation_token IS NOT NULL
           AND (
                NEW.confirmation_token IS DISTINCT FROM OLD.confirmation_token
                OR NEW.revision IS DISTINCT FROM OLD.revision
                OR NEW.status IS DISTINCT FROM OLD.status
           ) THEN
            terminal_state := CASE
                WHEN NEW.status IN ('CONFIRMED','EXECUTING','COMPLETED') THEN 'CONSUMED'
                WHEN NEW.status = 'CANCELLED' THEN 'CANCELLED'
                WHEN NEW.status = 'EXPIRED' THEN 'EXPIRED'
                ELSE 'INVALIDATED'
            END;
            UPDATE operation_confirmation
               SET state = terminal_state,
                   resolved_at = CASE
                       WHEN terminal_state IN ('CONSUMED','CANCELLED','EXPIRED') THEN COALESCE(resolved_at, NOW())
                       ELSE resolved_at
                   END,
                   updated_at = NOW()
             WHERE business_id = OLD.business_id
               AND operation_id = OLD.id
               AND operation_revision = OLD.revision
               AND token = OLD.confirmation_token
               AND state = 'AWAITING';
        END IF;

        -- A token is bound to one exact revision. If buggy/legacy code advances
        -- only the revision while reusing the same token, invalidate the old row
        -- above but do not mint a new confirmation with that stale token. The
        -- operation remains fail-closed until the application rotates the token.
        should_issue := NEW.status = 'AWAITING_CONFIRMATION'
            AND NEW.confirmation_token IS NOT NULL
            AND (
                OLD.confirmation_token IS DISTINCT FROM NEW.confirmation_token
                OR OLD.status IS DISTINCT FROM NEW.status
            );
    ELSE
        should_issue := NEW.status = 'AWAITING_CONFIRMATION'
            AND NEW.confirmation_token IS NOT NULL;
    END IF;

    IF should_issue THEN
        INSERT INTO operation_confirmation (
            business_id, operation_id, operation_revision, token,
            state, issued_at, expires_at, updated_at
        ) VALUES (
            NEW.business_id, NEW.id, NEW.revision, NEW.confirmation_token,
            'AWAITING', NOW(), NOW() + INTERVAL '30 minutes', NOW()
        )
        ON CONFLICT (business_id, operation_id, operation_revision)
        DO UPDATE SET
            token = EXCLUDED.token,
            state = 'AWAITING',
            issued_at = NOW(),
            expires_at = NOW() + INTERVAL '30 minutes',
            resolved_at = NULL,
            resolved_channel = NULL,
            resolved_source_reference_id = NULL,
            updated_at = NOW();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_business_operation_confirmation
AFTER INSERT OR UPDATE OF confirmation_token, revision, status
ON business_operation
FOR EACH ROW EXECUTE FUNCTION sync_operation_confirmation_from_operation();
