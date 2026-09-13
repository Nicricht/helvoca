create table messaging_conversation (
    id uuid primary key,
    business_id uuid not null references business(id) on delete cascade,
    customer_id uuid references customer(id),
    channel varchar(30) not null,
    sender varchar(80) not null,
    recipient varchar(80) not null,
    opened_at timestamp with time zone not null,
    last_message_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_messaging_conversation_lookup
    on messaging_conversation(business_id, channel, sender, recipient, last_message_at desc);

create table messaging_message (
    id uuid primary key,
    conversation_id uuid not null references messaging_conversation(id) on delete cascade,
    external_message_id varchar(100) unique,
    direction varchar(20) not null,
    role varchar(20) not null,
    content text not null,
    reply_text text,
    created_at timestamp with time zone not null
);

create index idx_messaging_message_conversation_created
    on messaging_message(conversation_id, created_at asc);

alter table business_subscription
    add column billing_provider varchar(30),
    add column pending_plan_code varchar(20),
    add column billing_checkout_url text;

alter table business_subscription
    add constraint ck_business_subscription_pending_plan
    check (pending_plan_code is null or pending_plan_code in ('BASIC','PRO','BUSINESS','ENTERPRISE'));
