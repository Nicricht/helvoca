-- V93 Tenant Integrity v1 closure
--
-- Close the remaining direct tenant-owned FK gaps discovered by the schema
-- architecture guard. Preserve each historical ON DELETE behavior while making
-- the database reject cross-tenant references even for owner/SYSTEM writes.
--
-- business_operation_item and business_order_line predate the direct
-- business_id convention. They now carry the tenant identity so PostgreSQL can
-- enforce parent + catalog + variant ownership declaratively.

ALTER TABLE public.calendar_integration
    ADD CONSTRAINT uq_calendar_integration_id_business UNIQUE (id, business_id);

ALTER TABLE public.booking_incident_campaign
    ADD CONSTRAINT uq_booking_incident_campaign_id_business UNIQUE (id, business_id);

ALTER TABLE public.knowledge_item
    ADD CONSTRAINT uq_knowledge_item_id_business UNIQUE (id, business_id);

ALTER TABLE public.human_handoff
    ADD CONSTRAINT uq_human_handoff_id_business UNIQUE (id, business_id);

ALTER TABLE public.call_session
    ADD CONSTRAINT fk_call_session_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE SET NULL (customer_id);

ALTER TABLE public.call_action
    ADD CONSTRAINT fk_call_action_call_tenant
        FOREIGN KEY (call_id, business_id)
        REFERENCES public.call_session(id, business_id);

ALTER TABLE public.messaging_conversation
    ADD CONSTRAINT fk_messaging_conversation_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id);

ALTER TABLE public.unanswered_question
    ADD CONSTRAINT fk_unanswered_question_call_tenant
        FOREIGN KEY (call_id, business_id)
        REFERENCES public.call_session(id, business_id),
    ADD CONSTRAINT fk_unanswered_question_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id),
    ADD CONSTRAINT fk_unanswered_question_knowledge_tenant
        FOREIGN KEY (knowledge_item_id, business_id)
        REFERENCES public.knowledge_item(id, business_id);

ALTER TABLE public.booking_calendar_event
    ADD CONSTRAINT fk_booking_calendar_event_integration_tenant
        FOREIGN KEY (integration_id, business_id)
        REFERENCES public.calendar_integration(id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.booking_incident_recipient
    ADD CONSTRAINT fk_booking_incident_recipient_campaign_tenant
        FOREIGN KEY (campaign_id, business_id)
        REFERENCES public.booking_incident_campaign(id, business_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_booking_incident_recipient_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE CASCADE;

ALTER TABLE public.human_handoff
    ADD CONSTRAINT fk_human_handoff_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id)
        ON DELETE SET NULL (customer_id),
    ADD CONSTRAINT fk_human_handoff_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES public.business_operation(id, business_id)
        ON DELETE SET NULL (operation_id);

ALTER TABLE public.human_handoff_event
    ADD CONSTRAINT fk_human_handoff_event_handoff_tenant
        FOREIGN KEY (handoff_id, business_id)
        REFERENCES public.human_handoff(id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.outbound_message
    ADD CONSTRAINT fk_outbound_message_catalog_item_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id)
        ON DELETE SET NULL (catalog_item_id);

ALTER TABLE public.business_operation_item
    ADD COLUMN business_id UUID;

UPDATE public.business_operation_item item
   SET business_id = parent.business_id
  FROM public.business_operation parent
 WHERE parent.id = item.operation_id;

ALTER TABLE public.business_operation_item
    ALTER COLUMN business_id SET NOT NULL,
    ADD CONSTRAINT fk_business_operation_item_operation_tenant
        FOREIGN KEY (operation_id, business_id)
        REFERENCES public.business_operation(id, business_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_business_operation_item_catalog_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_business_operation_item_variant_tenant
        FOREIGN KEY (variant_id, catalog_item_id, business_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.business_order_line
    ADD COLUMN business_id UUID;

UPDATE public.business_order_line line
   SET business_id = parent.business_id
  FROM public.business_order parent
 WHERE parent.id = line.order_id;

ALTER TABLE public.business_order_line
    ALTER COLUMN business_id SET NOT NULL,
    ADD CONSTRAINT fk_business_order_line_order_tenant
        FOREIGN KEY (order_id, business_id)
        REFERENCES public.business_order(id, business_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_business_order_line_catalog_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id),
    ADD CONSTRAINT fk_business_order_line_variant_tenant
        FOREIGN KEY (variant_id, catalog_item_id, business_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id, business_id)
        ON DELETE RESTRICT;
