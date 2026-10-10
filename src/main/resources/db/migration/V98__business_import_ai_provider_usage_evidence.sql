-- V98: Privacy-minimized, append-only receipts for paid document import.
-- A STARTED row is committed before the provider request: a timeout cannot
-- silently disappear from the finance review queue. Provider invoice amounts
-- are intentionally NOT inferred from per-request token estimates.
CREATE TABLE public.business_import_ai_provider_usage_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    attempt_id UUID NOT NULL,
    phase VARCHAR(16) NOT NULL,
    requested_model VARCHAR(100) NOT NULL,
    provider_model VARCHAR(100),
    provider_response_id VARCHAR(180),
    provider_request_id VARCHAR(180),
    http_status INTEGER,
    input_tokens BIGINT,
    cached_input_tokens BIGINT,
    output_tokens BIGINT,
    estimated_cost_usd NUMERIC(18,8),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_import_ai_attempt_phase UNIQUE (business_id, attempt_id, phase),
    CONSTRAINT ck_business_import_ai_phase CHECK (phase IN ('STARTED','RESPONSE','UNCERTAIN')),
    CONSTRAINT ck_business_import_ai_requested_model CHECK (length(btrim(requested_model)) > 0),
    CONSTRAINT ck_business_import_ai_http_status CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_business_import_ai_input CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT ck_business_import_ai_cached CHECK (cached_input_tokens IS NULL OR cached_input_tokens >= 0),
    CONSTRAINT ck_business_import_ai_output CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CONSTRAINT ck_business_import_ai_cached_subset CHECK (
        input_tokens IS NULL OR cached_input_tokens IS NULL OR cached_input_tokens <= input_tokens
    ),
    CONSTRAINT ck_business_import_ai_cost CHECK (
        estimated_cost_usd IS NULL OR estimated_cost_usd >= 0
    ),
    CONSTRAINT ck_business_import_ai_phase_payload CHECK (
        phase = 'RESPONSE'
        OR (provider_response_id IS NULL AND provider_request_id IS NULL
            AND provider_model IS NULL AND http_status IS NULL
            AND input_tokens IS NULL AND cached_input_tokens IS NULL
            AND output_tokens IS NULL AND estimated_cost_usd IS NULL)
    )
);
CREATE INDEX idx_business_import_ai_usage_tenant_time
    ON public.business_import_ai_provider_usage_event (business_id, recorded_at DESC);
CREATE INDEX idx_business_import_ai_usage_provider_time
    ON public.business_import_ai_provider_usage_event (phase, recorded_at DESC);
GRANT SELECT, INSERT ON public.business_import_ai_provider_usage_event TO helvoca_runtime, helvoca_system;
REVOKE UPDATE, DELETE ON public.business_import_ai_provider_usage_event
    FROM PUBLIC, helvoca_runtime, helvoca_system;
ALTER TABLE public.business_import_ai_provider_usage_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_import_ai_provider_usage_event FORCE ROW LEVEL SECURITY;
DO $$
DECLARE migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.business_import_ai_provider_usage_event TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;
COMMENT ON TABLE public.business_import_ai_provider_usage_event IS
    'Append-only per-attempt OpenAI Responses usage evidence for business imports. Token estimates are NOT reconciled provider invoice charges. Never store prompts, filenames, file bodies, credentials or response text.';
