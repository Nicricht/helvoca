-- V97: provider-authenticated Meta WhatsApp receipt ledger for V96 correlations.
-- Delivery means only the provider reported DELIVERED/READ, never that a
-- customer accepted an answer or physical business work was completed.
ALTER TABLE public.business_request_reply_correlation
    ADD CONSTRAINT uq_request_reply_correlation_tenant_message
        UNIQUE (id, business_id, inbound_message_id);

CREATE TABLE public.business_request_reply_delivery_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    correlation_id UUID NOT NULL,
    inbound_message_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL DEFAULT 'META_WHATSAPP_CLOUD',
    provider_message_id VARCHAR(180) NOT NULL,
    receipt_status VARCHAR(20) NOT NULL,
    provider_recorded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_request_reply_delivery_correlation
        FOREIGN KEY (correlation_id, business_id, inbound_message_id)
        REFERENCES public.business_request_reply_correlation(id, business_id, inbound_message_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_request_reply_delivery_provider
        CHECK (provider = 'META_WHATSAPP_CLOUD'),
    CONSTRAINT ck_request_reply_delivery_status
        CHECK (receipt_status IN ('DELIVERED','READ')),
    CONSTRAINT ck_request_reply_delivery_id_length
        CHECK (char_length(btrim(provider_message_id)) > 0),
    CONSTRAINT uq_request_reply_delivery_status
        UNIQUE (business_id, correlation_id, receipt_status)
);

CREATE INDEX idx_request_reply_delivery_message
    ON public.business_request_reply_delivery_event (business_id, inbound_message_id);

-- Protect against a future accidental application write that tries to
-- promote an unacknowledged, unmatched, or cross-tenant message.
CREATE FUNCTION public.guard_request_reply_delivery_evidence()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM public.business_request_reply_correlation corr
          JOIN public.messaging_conversation c
            ON c.id = corr.conversation_id AND c.business_id = corr.business_id
          JOIN public.messaging_message m
            ON m.id = corr.inbound_message_id AND m.conversation_id = c.id
         WHERE corr.id = NEW.correlation_id
           AND corr.business_id = NEW.business_id
           AND corr.inbound_message_id = NEW.inbound_message_id
           AND m.direction = 'INBOUND'
           AND m.role = 'USER'
           AND m.provider = NEW.provider
           AND m.provider_message_id = NEW.provider_message_id
           AND m.provider_delivery_status = NEW.receipt_status
           AND m.delivered_at IS NOT NULL
           AND (NEW.receipt_status <> 'READ' OR m.read_at IS NOT NULL)
           AND NEW.provider_recorded_at = CASE
               WHEN NEW.receipt_status = 'READ' THEN m.read_at
               ELSE m.delivered_at
           END
    ) THEN
        RAISE EXCEPTION 'No confirmed tenant-scoped provider reply receipt matches this request'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_guard_request_reply_delivery_evidence
BEFORE INSERT ON public.business_request_reply_delivery_event
FOR EACH ROW EXECUTE FUNCTION public.guard_request_reply_delivery_evidence();

GRANT SELECT, INSERT ON public.business_request_reply_delivery_event
    TO helvoca_runtime, helvoca_system;
REVOKE UPDATE, DELETE ON public.business_request_reply_delivery_event
    FROM PUBLIC, helvoca_runtime, helvoca_system;

ALTER TABLE public.business_request_reply_delivery_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_request_reply_delivery_event FORCE ROW LEVEL SECURITY;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.business_request_reply_delivery_event TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;

COMMENT ON TABLE public.business_request_reply_delivery_event IS
    'Append-only provider-verified WhatsApp reply receipts. Not business-work resolution; deletion only through correlation/request/message retention cascade.';
