-- Immutable, tenant-scoped event history for every universal business operation.
-- The database is the authoritative capture layer so new workflows/adapters
-- cannot accidentally bypass event creation.
-- IDs are intentionally snapshots rather than foreign keys: audit history must
-- survive resource deletion and must not block cleanup of operational tables.

CREATE TABLE business_operation_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_no BIGSERIAL NOT NULL UNIQUE,
    business_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    source_reference_id UUID,
    revision INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    previous_status VARCHAR(30),
    actor_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_operation_event_type
        CHECK (operation_type IN ('ORDER','QUOTE','LEAD','DELIVERY','REQUEST','BOOKING','PAYMENT')),
    CONSTRAINT ck_business_operation_event_channel
        CHECK (channel IN ('VOICE','WHATSAPP','MANUAL','API')),
    CONSTRAINT ck_business_operation_event_status
        CHECK (status IN ('DRAFT','AWAITING_CONFIRMATION','CONFIRMED','CANCELLED','EXPIRED','FAILED')),
    CONSTRAINT ck_business_operation_event_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('DRAFT','AWAITING_CONFIRMATION','CONFIRMED','CANCELLED','EXPIRED','FAILED')),
    CONSTRAINT ck_business_operation_event_actor
        CHECK (actor_type IN ('SYSTEM','HUMAN','AI','PROVIDER')),
    CONSTRAINT ck_business_operation_event_revision CHECK (revision > 0)
);

CREATE INDEX idx_business_operation_event_business_sequence
    ON business_operation_event(business_id, sequence_no DESC);
CREATE INDEX idx_business_operation_event_operation_sequence
    ON business_operation_event(business_id, operation_id, sequence_no DESC);
CREATE INDEX idx_business_operation_event_type_sequence
    ON business_operation_event(business_id, event_type, sequence_no DESC);

-- Seed one non-destructive baseline event for operations that existed before V29.
INSERT INTO business_operation_event (
    business_id,
    operation_id,
    operation_type,
    event_type,
    channel,
    source_reference_id,
    revision,
    status,
    previous_status,
    actor_type,
    payload_json,
    created_at
)
SELECT
    bo.business_id,
    bo.id,
    bo.type,
    bo.type || '_SNAPSHOT_IMPORTED',
    bo.source,
    bo.source_reference_id,
    GREATEST(COALESCE(bo.revision, 1), 1),
    bo.status,
    NULL,
    'SYSTEM',
    jsonb_strip_nulls(jsonb_build_object(
        'migrated', true,
        'total', bo.total,
        'currency', bo.currency,
        'fulfillmentType', bo.fulfillment_type,
        'deliveryFee', bo.delivery_fee
    )),
    COALESCE(bo.updated_at, bo.created_at, NOW())
FROM business_operation bo;

CREATE OR REPLACE FUNCTION append_business_operation_event()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_row business_operation%ROWTYPE;
    v_event_type VARCHAR(80);
    v_previous_status VARCHAR(30);
    v_payment_status_before TEXT;
    v_payment_status_after TEXT;
    v_actor_type VARCHAR(20);
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_row := OLD;
        v_previous_status := OLD.status;
        v_event_type := OLD.type || '_DELETED';
        v_payment_status_before := CASE WHEN OLD.metadata_json IS NULL THEN NULL ELSE OLD.metadata_json ->> 'paymentStatus' END;
        v_payment_status_after := NULL;
    ELSIF TG_OP = 'INSERT' THEN
        v_row := NEW;
        v_previous_status := NULL;
        v_event_type := CASE NEW.type
            WHEN 'ORDER' THEN CASE WHEN NEW.status = 'AWAITING_CONFIRMATION' THEN 'ORDER_QUOTED' ELSE 'ORDER_CREATED' END
            WHEN 'DELIVERY' THEN CASE WHEN NEW.status = 'AWAITING_CONFIRMATION' THEN 'DELIVERY_QUOTED' ELSE 'DELIVERY_CREATED' END
            WHEN 'PAYMENT' THEN CASE WHEN NEW.status = 'AWAITING_CONFIRMATION' THEN 'PAYMENT_QUOTED' ELSE 'PAYMENT_CREATED' END
            WHEN 'QUOTE' THEN 'QUOTE_CREATED'
            WHEN 'LEAD' THEN 'LEAD_CREATED'
            WHEN 'REQUEST' THEN 'REQUEST_CREATED'
            WHEN 'BOOKING' THEN 'BOOKING_CREATED'
            ELSE NEW.type || '_CREATED'
        END;
    ELSE
        v_row := NEW;
        v_previous_status := OLD.status;
        v_payment_status_before := CASE WHEN OLD.metadata_json IS NULL THEN NULL ELSE OLD.metadata_json ->> 'paymentStatus' END;
        v_payment_status_after := CASE WHEN NEW.metadata_json IS NULL THEN NULL ELSE NEW.metadata_json ->> 'paymentStatus' END;

        IF NEW.type = 'PAYMENT'
           AND v_payment_status_before IS DISTINCT FROM v_payment_status_after
           AND v_payment_status_after IS NOT NULL THEN
            v_event_type := 'PAYMENT_STATUS_CHANGED';
        ELSIF OLD.status IS DISTINCT FROM NEW.status THEN
            v_event_type := NEW.type || '_' || CASE NEW.status
                WHEN 'CONFIRMED' THEN 'CONFIRMED'
                WHEN 'CANCELLED' THEN 'CANCELLED'
                WHEN 'FAILED' THEN 'FAILED'
                WHEN 'EXPIRED' THEN 'EXPIRED'
                WHEN 'AWAITING_CONFIRMATION' THEN 'AWAITING_CONFIRMATION'
                WHEN 'DRAFT' THEN 'DRAFTED'
                ELSE 'STATUS_CHANGED'
            END;
        ELSIF OLD.revision IS DISTINCT FROM NEW.revision
           OR OLD.total IS DISTINCT FROM NEW.total
           OR OLD.currency IS DISTINCT FROM NEW.currency
           OR OLD.fulfillment_type IS DISTINCT FROM NEW.fulfillment_type
           OR OLD.delivery_zone_id IS DISTINCT FROM NEW.delivery_zone_id
           OR OLD.delivery_fee IS DISTINCT FROM NEW.delivery_fee
           OR OLD.metadata_json IS DISTINCT FROM NEW.metadata_json THEN
            v_event_type := NEW.type || '_UPDATED';
        ELSE
            RETURN NEW;
        END IF;
    END IF;

    v_actor_type := CASE
        WHEN v_event_type = 'PAYMENT_STATUS_CHANGED' THEN 'PROVIDER'
        WHEN v_row.source = 'MANUAL' THEN 'HUMAN'
        WHEN v_row.source IN ('VOICE','WHATSAPP') THEN 'AI'
        ELSE 'SYSTEM'
    END;

    INSERT INTO business_operation_event (
        business_id,
        operation_id,
        operation_type,
        event_type,
        channel,
        source_reference_id,
        revision,
        status,
        previous_status,
        actor_type,
        payload_json
    ) VALUES (
        v_row.business_id,
        v_row.id,
        v_row.type,
        v_event_type,
        v_row.source,
        v_row.source_reference_id,
        GREATEST(COALESCE(v_row.revision, 1), 1),
        v_row.status,
        v_previous_status,
        v_actor_type,
        jsonb_strip_nulls(jsonb_build_object(
            'previousStatus', v_previous_status,
            'status', v_row.status,
            'total', v_row.total,
            'currency', v_row.currency,
            'fulfillmentType', v_row.fulfillment_type,
            'deliveryFee', v_row.delivery_fee,
            'paymentStatusBefore', v_payment_status_before,
            'paymentStatusAfter', v_payment_status_after
        ))
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_business_operation_event_insert
AFTER INSERT ON business_operation
FOR EACH ROW
EXECUTE FUNCTION append_business_operation_event();

CREATE TRIGGER trg_business_operation_event_update
AFTER UPDATE ON business_operation
FOR EACH ROW
EXECUTE FUNCTION append_business_operation_event();

CREATE TRIGGER trg_business_operation_event_delete
AFTER DELETE ON business_operation
FOR EACH ROW
EXECUTE FUNCTION append_business_operation_event();

CREATE OR REPLACE FUNCTION reject_business_operation_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'business_operation_event is append-only';
END;
$$;

CREATE TRIGGER trg_business_operation_event_immutable
BEFORE UPDATE OR DELETE ON business_operation_event
FOR EACH ROW
EXECUTE FUNCTION reject_business_operation_event_mutation();
