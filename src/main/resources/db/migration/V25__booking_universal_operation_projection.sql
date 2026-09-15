-- BOOKING becomes a typed projection of the universal operation envelope.
-- Existing booking availability/ownership logic remains unchanged. Database
-- triggers guarantee that even a future direct booking insert/update cannot
-- bypass the universal operation projection.

ALTER TABLE business_operation
    DROP CONSTRAINT ck_business_operation_type;

ALTER TABLE business_operation
    ADD CONSTRAINT ck_business_operation_type
        CHECK (type IN ('ORDER','QUOTE','LEAD','DELIVERY','REQUEST','BOOKING'));

ALTER TABLE booking
    ADD COLUMN operation_id UUID;

-- Historical rows reuse the booking UUID as operation UUID. This makes the
-- backfill deterministic and avoids inventing a second identifier for legacy
-- bookings.
INSERT INTO business_operation (
    id, business_id, customer_id, source_reference_id, type, status, source,
    revision, confirmation_token, currency, metadata_json, created_at, updated_at
)
SELECT
    b.id,
    b.business_id,
    b.customer_id,
    NULL,
    'BOOKING',
    CASE WHEN b.status = 'CANCELLED' THEN 'CANCELLED' ELSE 'CONFIRMED' END,
    CASE b.source
        WHEN 'AI_CALL' THEN 'VOICE'
        WHEN 'AI_WHATSAPP' THEN 'WHATSAPP'
        ELSE 'MANUAL'
    END,
    1,
    NULL,
    'CLP',
    jsonb_strip_nulls(jsonb_build_object(
        'intent', 'BOOKING',
        'bookingId', b.id::text,
        'serviceId', b.service_id::text,
        'startAt', b.start_at,
        'endAt', b.end_at,
        'notes', b.notes,
        'projectionStatus', b.status,
        'migrated', true
    )),
    b.created_at,
    b.updated_at
FROM booking b
ON CONFLICT (id) DO NOTHING;

UPDATE booking
SET operation_id = id
WHERE operation_id IS NULL;

ALTER TABLE booking
    ALTER COLUMN operation_id SET NOT NULL,
    ADD CONSTRAINT fk_booking_operation
        FOREIGN KEY (operation_id) REFERENCES business_operation(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_booking_operation UNIQUE (operation_id);

CREATE INDEX idx_booking_operation ON booking(operation_id);

CREATE OR REPLACE FUNCTION ensure_booking_operation_projection()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.operation_id IS NULL THEN
        NEW.operation_id := gen_random_uuid();
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

CREATE TRIGGER trg_booking_operation_insert
BEFORE INSERT ON booking
FOR EACH ROW
EXECUTE FUNCTION ensure_booking_operation_projection();

CREATE OR REPLACE FUNCTION sync_booking_operation_projection()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.business_id IS DISTINCT FROM OLD.business_id
       OR NEW.customer_id IS DISTINCT FROM OLD.customer_id
       OR NEW.service_id IS DISTINCT FROM OLD.service_id
       OR NEW.start_at IS DISTINCT FROM OLD.start_at
       OR NEW.end_at IS DISTINCT FROM OLD.end_at
       OR NEW.status IS DISTINCT FROM OLD.status
       OR NEW.source IS DISTINCT FROM OLD.source
       OR NEW.notes IS DISTINCT FROM OLD.notes THEN

        UPDATE business_operation
        SET business_id = NEW.business_id,
            customer_id = NEW.customer_id,
            type = 'BOOKING',
            status = CASE WHEN NEW.status = 'CANCELLED' THEN 'CANCELLED' ELSE 'CONFIRMED' END,
            source = CASE NEW.source
                WHEN 'AI_CALL' THEN 'VOICE'
                WHEN 'AI_WHATSAPP' THEN 'WHATSAPP'
                ELSE 'MANUAL'
            END,
            revision = revision + 1,
            confirmation_token = NULL,
            metadata_json = COALESCE(metadata_json, '{}'::jsonb)
                || jsonb_strip_nulls(jsonb_build_object(
                    'intent', 'BOOKING',
                    'bookingId', NEW.id::text,
                    'serviceId', NEW.service_id::text,
                    'startAt', NEW.start_at,
                    'endAt', NEW.end_at,
                    'notes', NEW.notes,
                    'projectionStatus', NEW.status
                )),
            updated_at = NOW()
        WHERE id = NEW.operation_id
          AND business_id = OLD.business_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_operation_update
AFTER UPDATE OF business_id, customer_id, service_id, start_at, end_at, status, source, notes
ON booking
FOR EACH ROW
EXECUTE FUNCTION sync_booking_operation_projection();
