-- V80 Booking attendance and payment truth
--
-- Adds durable payment provenance and backfills historical successful provider
-- payments. It also gives legacy/admin BOOKING projections the authoritative
-- service price required by the universal payment workflow.

ALTER TABLE business_payment
    ADD COLUMN verification_method VARCHAR(30) NOT NULL DEFAULT 'PROVIDER',
    ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN verified_at TIMESTAMPTZ;

ALTER TABLE business_payment
    ADD CONSTRAINT ck_business_payment_verification_method
        CHECK (verification_method IN ('PROVIDER','MANUAL_BUSINESS')),
    ADD CONSTRAINT ck_business_payment_method
        CHECK (payment_method IN ('ONLINE','CASH','CARD','TRANSFER','OTHER'));

UPDATE business_payment
SET verified_at = updated_at
WHERE status = 'SUCCEEDED'
  AND verified_at IS NULL;

UPDATE business_operation bo
SET total = s.price,
    currency = 'CLP'
FROM booking b
JOIN service s
  ON s.id = b.service_id
 AND s.business_id = b.business_id
WHERE bo.id = b.operation_id
  AND bo.business_id = b.business_id
  AND bo.type = 'BOOKING'
  AND bo.total IS NULL
  AND s.price IS NOT NULL;

CREATE OR REPLACE FUNCTION ensure_booking_operation_projection()
RETURNS TRIGGER AS $$
DECLARE
    service_price NUMERIC(12,2);
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

    SELECT price
      INTO service_price
      FROM service
     WHERE id = NEW.service_id
       AND business_id = NEW.business_id;

    INSERT INTO business_operation (
        id, business_id, customer_id, source_reference_id, type, status, source,
        revision, confirmation_token, total, currency, metadata_json, created_at, updated_at
    )
    VALUES (
        NEW.operation_id,
        NEW.business_id,
        NEW.customer_id,
        NULL,
        'BOOKING',
        CASE
            WHEN NEW.status IN ('CANCELLED','NO_SHOW') THEN 'CANCELLED'
            WHEN NEW.status = 'COMPLETED' THEN 'COMPLETED'
            ELSE 'CONFIRMED'
        END,
        CASE NEW.source
            WHEN 'AI_CALL' THEN 'VOICE'
            WHEN 'AI_WHATSAPP' THEN 'WHATSAPP'
            ELSE 'MANUAL'
        END,
        1,
        NULL,
        service_price,
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
