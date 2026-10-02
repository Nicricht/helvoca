-- V92 Conversation operation state tenant integrity hardening
--
-- conversation_operation_state belongs to one business and may reference an
-- active business_operation. The referenced operation must belong to the same
-- tenant even for privileged owner/SYSTEM-equivalent writes.
--
-- Preserve the original delete semantics by clearing only active_operation_id.

ALTER TABLE public.conversation_operation_state
    ADD CONSTRAINT fk_conversation_operation_state_active_operation_tenant
        FOREIGN KEY (active_operation_id, business_id)
        REFERENCES public.business_operation(id, business_id)
        ON DELETE SET NULL (active_operation_id);
