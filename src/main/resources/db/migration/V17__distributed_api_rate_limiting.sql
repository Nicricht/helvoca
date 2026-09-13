CREATE TABLE api_rate_limit_bucket (
    bucket_key VARCHAR(180) NOT NULL,
    window_start BIGINT NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (bucket_key, window_start)
);

CREATE INDEX idx_api_rate_limit_bucket_expires_at
    ON api_rate_limit_bucket (expires_at);
