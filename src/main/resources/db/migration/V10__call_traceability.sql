create table call_action (
    id uuid primary key,
    business_id uuid not null references business(id),
    call_id uuid not null references call_session(id),
    action_type varchar(80) not null,
    success boolean not null,
    entity_type varchar(50),
    entity_id uuid,
    detail text,
    error_code varchar(80),
    created_at timestamp with time zone not null
);

create index idx_call_action_call_created
    on call_action(call_id, created_at asc);
create index idx_call_action_business_created
    on call_action(business_id, created_at desc);
