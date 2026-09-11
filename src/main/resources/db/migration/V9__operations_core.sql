create table business_request (
    id uuid primary key,
    business_id uuid not null references business(id),
    customer_id uuid references customer(id),
    call_id uuid references call_session(id),
    request_type varchar(80) not null,
    title varchar(200) not null,
    description text,
    contact_name varchar(200),
    contact_phone varchar(30),
    priority varchar(20) not null default 'NORMAL',
    status varchar(30) not null default 'OPEN',
    source varchar(30) not null default 'MANUAL',
    details_json text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_business_request_business_created
    on business_request(business_id, created_at desc);
create index idx_business_request_business_status
    on business_request(business_id, status);

create table unanswered_question (
    id uuid primary key,
    business_id uuid not null references business(id),
    call_id uuid references call_session(id),
    customer_id uuid references customer(id),
    question text not null,
    normalized_question varchar(500),
    occurrences integer not null default 1,
    status varchar(30) not null default 'OPEN',
    answer text,
    knowledge_item_id uuid references knowledge_item(id),
    first_seen_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    answered_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_unanswered_question_business_status
    on unanswered_question(business_id, status, last_seen_at desc);
