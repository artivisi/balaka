-- V002: Security Data
-- Insert default admin user with bcrypt-hashed credential
-- IMPORTANT: Change this after first deployment

INSERT INTO users (id, username, password, full_name, email, active, created_at, updated_at)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'admin',
    '$2a$10$mMan.18CFTqJA/FVpkJr3OgCD0uTuhF9Enjf99QHm9tWPJH.nCj5S',
    'Administrator',
    'admin@artivisi.com',
    TRUE,
    NOW(),
    NOW()
);

-- Assign ADMIN role to default admin user
INSERT INTO user_roles (id, id_user, role, created_at, created_by)
VALUES (
    'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'ADMIN',
    NOW(),
    'system'
);
