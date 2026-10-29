ALTER TABLE twilio_certification_command
    ADD COLUMN business_id UUID REFERENCES business(id);

CREATE INDEX ix_twilio_certification_demo_pending
    ON twilio_certification_command (requested_at)
    WHERE status = 'PENDING' AND business_id IS NOT NULL;

REVOKE ALL ON TABLE public.twilio_certification_command FROM PUBLIC, helvoca_runtime;
GRANT SELECT ON TABLE public.twilio_certification_command TO helvoca_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.twilio_certification_command TO helvoca_system;
ALTER TABLE public.twilio_certification_command ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_certification_command FORCE ROW LEVEL SECURITY;
CREATE POLICY helvoca_system_certification_command
    ON public.twilio_certification_command
    FOR ALL TO helvoca_system USING (true) WITH CHECK (true);
CREATE POLICY helvoca_tenant_read_certification_command
    ON public.twilio_certification_command
    FOR SELECT TO helvoca_runtime
    USING (business_id = public.helvoca_rls_business_id());
