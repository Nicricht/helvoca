ALTER TABLE audit_log
    ADD COLUMN actor_type VARCHAR(20),
    ADD COLUMN actor_user_id UUID,
    ADD COLUMN actor_name VARCHAR(150),
    ADD COLUMN actor_email VARCHAR(180),
    ADD COLUMN actor_role VARCHAR(50);

ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX idx_audit_actor_user_created
    ON audit_log(actor_user_id, created_at DESC);
