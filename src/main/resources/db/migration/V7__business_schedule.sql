CREATE TABLE business_hours (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_business_hours_range CHECK (open_time < close_time)
);

CREATE INDEX idx_business_hours_business_day
    ON business_hours(business_id, day_of_week, open_time);

CREATE TABLE business_schedule_exception (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT TRUE,
    open_time TIME,
    close_time TIME,
    reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_business_schedule_exception_date UNIQUE (business_id, exception_date),
    CONSTRAINT ck_business_schedule_exception_hours CHECK (
        (closed = TRUE AND open_time IS NULL AND close_time IS NULL)
        OR
        (closed = FALSE AND open_time IS NOT NULL AND close_time IS NOT NULL AND open_time < close_time)
    )
);

CREATE INDEX idx_business_schedule_exception_business_date
    ON business_schedule_exception(business_id, exception_date);
