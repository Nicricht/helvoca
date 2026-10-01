INSERT INTO role(code, name) VALUES
('BUSINESS_OWNER', 'Business Owner'),
('MANAGER', 'Manager'),
('RECEPTION', 'Reception'),
('STAFF', 'Staff'),
('KITCHEN', 'Kitchen'),
('DISPATCH', 'Dispatch'),
('PROFESSIONAL', 'Professional'),
('WAREHOUSE', 'Warehouse'),
('SALES', 'Sales')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE team_invitation
    DROP CONSTRAINT IF EXISTS ck_team_invitation_role;

ALTER TABLE team_invitation
    ADD CONSTRAINT ck_team_invitation_role CHECK (
        role_code IN (
            'BUSINESS_ADMIN',
            'MANAGER',
            'RECEPTION',
            'STAFF',
            'KITCHEN',
            'DISPATCH',
            'PROFESSIONAL',
            'WAREHOUSE',
            'SALES',
            'OPERATOR'
        )
    );
