-- Consolidate commercial tool authorization into ai_agent_capability.
-- business_operation_capability remains as a deprecated compatibility table
-- for migration history only; runtime authorization no longer reads it.

WITH inserted_agents AS (
    INSERT INTO ai_agent (
        business_id,
        name,
        language,
        voice,
        greeting,
        instructions,
        active,
        created_at,
        updated_at
    )
    SELECT
        b.id,
        'RecepVoz',
        b.language,
        NULL,
        'Hola, gracias por llamar a ' || b.name || '. ¿En qué puedo ayudarte?',
        NULL,
        TRUE,
        NOW(),
        NOW()
    FROM business b
    WHERE EXISTS (
        SELECT 1
        FROM business_operation_capability boc
        WHERE boc.business_id = b.id
    )
      AND NOT EXISTS (
        SELECT 1
        FROM ai_agent aa
        WHERE aa.business_id = b.id
    )
    RETURNING id
)
INSERT INTO ai_agent_capability (ai_agent_id, capability)
SELECT inserted_agents.id, legacy.capability
FROM inserted_agents
CROSS JOIN (VALUES
    ('GET_BUSINESS_INFORMATION'),
    ('LIST_SERVICES'),
    ('SEARCH_KNOWLEDGE'),
    ('FIND_CALLER'),
    ('REGISTER_CALLER'),
    ('LIST_AVAILABLE_SLOTS'),
    ('CHECK_BOOKING_AVAILABILITY'),
    ('CREATE_BOOKING'),
    ('LIST_CUSTOMER_BOOKINGS'),
    ('RESCHEDULE_BOOKING'),
    ('CANCEL_BOOKING'),
    ('CREATE_REQUEST'),
    ('RECORD_UNANSWERED_QUESTION'),
    ('TRANSFER_TO_HUMAN')
) AS legacy(capability)
ON CONFLICT (ai_agent_id, capability) DO NOTHING;

-- Map the old high-level grants to concrete AiCapability values. Dependency
-- tools are included here as a defensive migration rule even if an older row
-- was written without the normalization performed by the application service.
INSERT INTO ai_agent_capability (ai_agent_id, capability)
SELECT aa.id, mapping.ai_capability
FROM business_operation_capability boc
JOIN ai_agent aa
  ON aa.business_id = boc.business_id
JOIN (VALUES
    ('CATALOG',  'LIST_CATALOG'),

    ('ORDER',    'LIST_CATALOG'),
    ('ORDER',    'QUOTE_ORDER'),
    ('ORDER',    'CREATE_ORDER'),
    ('ORDER',    'GET_ORDER_STATUS'),
    ('ORDER',    'CANCEL_ORDER'),

    ('DELIVERY', 'LIST_CATALOG'),
    ('DELIVERY', 'QUOTE_ORDER'),
    ('DELIVERY', 'CREATE_ORDER'),
    ('DELIVERY', 'GET_ORDER_STATUS'),
    ('DELIVERY', 'CANCEL_ORDER'),
    ('DELIVERY', 'LIST_DELIVERY_ZONES'),
    ('DELIVERY', 'VALIDATE_DELIVERY_ADDRESS'),

    ('QUOTE',    'LIST_CATALOG'),
    ('QUOTE',    'CREATE_QUOTE'),

    ('LEAD',     'CREATE_LEAD')
) AS mapping(business_capability, ai_capability)
  ON mapping.business_capability = boc.capability
ON CONFLICT (ai_agent_id, capability) DO NOTHING;

COMMENT ON TABLE business_operation_capability IS
    'Deprecated compatibility table. Runtime commercial tool authorization is stored in ai_agent_capability from V22 onward.';
