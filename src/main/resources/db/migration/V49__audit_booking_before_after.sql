ALTER TABLE audit_log
    ADD COLUMN before_json JSONB,
    ADD COLUMN after_json JSONB;
