CREATE TABLE pilot_launch_control (
    business_id UUID PRIMARY KEY REFERENCES business(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    responsible_name VARCHAR(180),
    responsible_contact VARCHAR(180),
    goal TEXT,
    planned_end_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pilot_launch_status
        CHECK (status IN ('DRAFT','READY','RUNNING','PAUSED','COMPLETED'))
);
