-- V38 Calendar / Meeting Integration Core
-- Provider-neutral, tenant-scoped calendar synchronization driven by the V37 durable job engine.

ALTER TABLE booking
    ADD CONSTRAINT uq_booking_id_business UNIQUE (id, business_id);

CREATE TABLE calendar_integration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    provider_code VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED',
    external_calendar_id VARCHAR(255),
    meetings_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    connected_at TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_calendar_integration_business UNIQUE (business_id),
    CONSTRAINT ck_calendar_integration_provider CHECK (length(trim(provider_code)) > 0),
    CONSTRAINT ck_calendar_integration_status CHECK (
        status IN ('DISCONNECTED','CONNECTED','ERROR')
    ),
    CONSTRAINT ck_calendar_integration_connected CHECK (
        status <> 'CONNECTED' OR (external_calendar_id IS NOT NULL AND length(trim(external_calendar_id)) > 0)
    )
);

CREATE TABLE booking_calendar_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    booking_id UUID NOT NULL,
    integration_id UUID NOT NULL REFERENCES calendar_integration(id) ON DELETE RESTRICT,
    provider_code VARCHAR(40) NOT NULL,
    external_event_id VARCHAR(255),
    meeting_url VARCHAR(1000),
    desired_version INTEGER NOT NULL DEFAULT 1,
    synced_version INTEGER NOT NULL DEFAULT 0,
    desired_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(500),
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_booking_calendar_event_booking_tenant
        FOREIGN KEY (booking_id, business_id)
        REFERENCES booking(id, business_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_booking_calendar_event_booking UNIQUE (business_id, booking_id),
    CONSTRAINT ck_booking_calendar_event_versions CHECK (
        desired_version >= 1 AND synced_version >= 0 AND synced_version <= desired_version
    ),
    CONSTRAINT ck_booking_calendar_event_status CHECK (
        status IN ('PENDING','SYNCED','FAILED','DELETED')
    )
);

CREATE INDEX idx_calendar_integration_status
    ON calendar_integration(status, business_id);

CREATE INDEX idx_booking_calendar_event_integration
    ON booking_calendar_event(integration_id, status, updated_at DESC);

CREATE INDEX idx_booking_calendar_event_business
    ON booking_calendar_event(business_id, updated_at DESC);
