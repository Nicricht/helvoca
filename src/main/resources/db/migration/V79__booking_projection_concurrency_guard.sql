-- V79 Booking projection concurrency guard
--
-- The booking projection trigger predates the two-phase universal booking flow.
-- For a confirmed universal booking the business_operation row already exists.
-- Re-running INSERT ... ON CONFLICT against that same row while another
-- transaction is updating it can surface PostgreSQL serialization conflicts
-- during otherwise idempotent concurrent confirmations.
--
-- Skip the legacy bootstrap INSERT when the universal operation already exists.
-- Direct/legacy booking inserts still create their operation projection exactly
-- as before, and ON CONFLICT remains the final race guard for that path.

CREATE OR REPLACE FUNCTION ensure_booking_operation_projection()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.operation_id IS NULL THEN
        NEW.operation_id := gen_random_uuid();
    END IF;

    IF EXISTS (
        SELECT 1
        FROM business_operation
        WHERE id = NEW.operation_id
    ) THEN
        RETURN NEW;
    END IF;

    INSERT INTO business_operation (
        id, business_id, customer_id, source_reference_id, type, status, source,
        revision, confirmation_token, currency, metadata_json, created_at, updated_at
    )
    VALUES (
        NEW.operation_id,
        NEW.business_id,
        NEW.customer_id,
        NULL,
        'BOOKING',
        CASE WHEN NEW.status = 'CANCELLED' THEN 'CANCELLED' ELSE 'CONFIRMED' END,
        CASE NEW.source
            WHEN 'AI_CALL' THEN 'VOICE'
            WHEN 'AI_WHATSAPP' THEN 'WHATSAPP'
            ELSE 'MANUAL'
        END,
        1,
        NULL,
        'CLP',
        jsonb_strip_nulls(jsonb_build_object(
            'intent', 'BOOKING',
            'bookingId', NEW.id::text,
            'serviceId', NEW.service_id::text,
            'startAt', NEW.start_at,
            'endAt', NEW.end_at,
            'notes', NEW.notes,
            'projectionStatus', NEW.status,
            'migrated', false
        )),
        COALESCE(NEW.created_at, NOW()),
        COALESCE(NEW.updated_at, NOW())
    )
    ON CONFLICT (id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
