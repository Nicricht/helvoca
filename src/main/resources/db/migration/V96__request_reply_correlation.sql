-- V96: immutable correlation between a verified AI-created WhatsApp request
-- and the exact persisted inbound message whose reply will be sent.
-- IMPORTANT: this is NOT evidence that the reply was delivered or the work resolved.
--
-- Composite keys bind every reference to the same tenant and conversation.
ALTER TABLE public.messaging_conversation
    ADD CONSTRAINT uq_messaging_conversation_id_business UNIQUE (id, business_id);

ALTER TABLE public.messaging_message
    ADD CONSTRAINT uq_messaging_message_id_conversation UNIQUE (id, conversation_id);

CREATE TABLE public.business_request_reply_correlation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    inbound_message_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_request_reply_request_tenant
        FOREIGN KEY (request_id, business_id)
        REFERENCES public.business_request(id, business_id) ON DELETE CASCADE,
    CONSTRAINT fk_request_reply_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES public.business_operation(id, business_id) ON DELETE CASCADE,
    CONSTRAINT fk_request_reply_conversation_tenant
        FOREIGN KEY (conversation_id, business_id)
        REFERENCES public.messaging_conversation(id, business_id) ON DELETE CASCADE,
    CONSTRAINT fk_request_reply_message_conversation
        FOREIGN KEY (inbound_message_id, conversation_id)
        REFERENCES public.messaging_message(id, conversation_id) ON DELETE CASCADE,
    CONSTRAINT uq_request_reply_message
        UNIQUE (business_id, request_id, inbound_message_id)
);

CREATE INDEX idx_request_reply_message
    ON public.business_request_reply_correlation(business_id, inbound_message_id);

-- Guard against synthetic or cross-tenant correlation even if a future code
-- path tries to insert IDs directly instead of using the validated service.
CREATE FUNCTION public.guard_request_reply_correlation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM public.business_request r
        JOIN public.business_operation op
          ON op.id = r.operation_id AND op.business_id = r.business_id
        JOIN public.messaging_conversation c
          ON c.id = op.source_reference_id AND c.business_id = r.business_id
        JOIN public.messaging_message m
          ON m.conversation_id = c.id
        WHERE r.id = NEW.request_id
          AND r.business_id = NEW.business_id
          AND r.operation_id = NEW.operation_id
          AND r.source = 'AI_WHATSAPP'
          AND op.type = 'REQUEST'
          AND op.source = 'WHATSAPP'
          AND c.id = NEW.conversation_id
          AND lower(c.channel) = 'whatsapp'
          AND m.id = NEW.inbound_message_id
          AND m.direction = 'INBOUND'
          AND m.role = 'USER'
          AND nullif(btrim(m.reply_text), '') IS NOT NULL
          AND EXISTS (
              SELECT 1 FROM public.business_operation_event ev
              WHERE ev.business_id = r.business_id
                AND ev.operation_id = op.id
                AND ev.source_reference_id = c.id
                AND ev.operation_type = 'REQUEST'
                AND ev.channel = 'WHATSAPP'
                AND ev.event_type = 'REQUEST_CREATED'
                AND ev.actor_type = 'AI'
          )
    ) THEN
        RAISE EXCEPTION 'Request/reply correlation is not backed by a trusted operation and persisted reply'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_guard_request_reply_correlation
BEFORE INSERT ON public.business_request_reply_correlation
FOR EACH ROW EXECUTE FUNCTION public.guard_request_reply_correlation();

GRANT SELECT, INSERT ON public.business_request_reply_correlation
    TO helvoca_runtime, helvoca_system;
REVOKE UPDATE, DELETE ON public.business_request_reply_correlation
    FROM PUBLIC, helvoca_runtime, helvoca_system;

ALTER TABLE public.business_request_reply_correlation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_request_reply_correlation FORCE ROW LEVEL SECURITY;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.business_request_reply_correlation TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;

COMMENT ON TABLE public.business_request_reply_correlation IS
    'Verified request to persisted WhatsApp reply linkage; delivery, correctness and resolution remain unconfirmed. Runtime append-only; retention cascades via owning request or message.';
