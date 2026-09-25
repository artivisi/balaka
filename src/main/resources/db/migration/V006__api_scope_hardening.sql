-- =============================================
-- V006: API scope hardening
--
-- 1. Widen device_tokens.scopes and api_clients.scopes: the full scope list
--    issued to device-flow tokens no longer fits in VARCHAR(255).
-- 2. New scopes guard endpoints that were previously reachable with other
--    scopes (payroll/employee/salary-component and fiscal adjustment writes
--    with tax-export:read) or with any token (templates, bank reconciliation).
--    Device-flow tokens were issued with every scope, so they receive the new
--    scopes that cover what they could already call:
--      tax-export:read   -> + payroll:write, tax-export:write
--      transactions:post -> + templates:read, templates:write,
--                             bank-recon:read, bank-recon:write
--      drafts:create     -> + templates:read, accounts:read
--    Least privilege is enforced per request by intersecting token scopes with
--    the token user's current role permissions.
-- 3. API clients (client_credentials) carry admin-chosen scopes. They get no new
--    write scope. Where a client posts transactions or creates drafts it gets
--    templates:read and accounts:read: both flows need template/account ids, and
--    GET /api/templates and GET /api/drafts/{templates,accounts} were unguarded
--    before. Client tokens are re-minted from api_clients.scopes, so existing
--    short-lived client tokens are left as issued.
-- =============================================

ALTER TABLE device_tokens ALTER COLUMN scopes TYPE VARCHAR(2000);
ALTER TABLE api_clients ALTER COLUMN scopes TYPE VARCHAR(2000);

CREATE TEMPORARY TABLE v006_device_scope_grants (existing VARCHAR(50), added VARCHAR(50)) ON COMMIT DROP;
INSERT INTO v006_device_scope_grants (existing, added) VALUES
    ('tax-export:read', 'payroll:write'),
    ('tax-export:read', 'tax-export:write'),
    ('transactions:post', 'templates:read'),
    ('transactions:post', 'templates:write'),
    ('transactions:post', 'bank-recon:read'),
    ('transactions:post', 'bank-recon:write'),
    ('drafts:create', 'templates:read'),
    ('drafts:create', 'accounts:read');

DO $$
DECLARE
    g RECORD;
BEGIN
    FOR g IN SELECT existing, added FROM v006_device_scope_grants LOOP
        UPDATE device_tokens t
           SET scopes = t.scopes || ',' || g.added
         WHERE t.scopes IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM api_clients c WHERE c.client_id = t.client_id)
           AND (',' || t.scopes || ',') LIKE ('%,' || g.existing || ',%')
           AND (',' || t.scopes || ',') NOT LIKE ('%,' || g.added || ',%');
    END LOOP;
END $$;

UPDATE api_clients
   SET scopes = scopes || ',templates:read'
 WHERE ((',' || scopes || ',') LIKE '%,transactions:post,%'
        OR (',' || scopes || ',') LIKE '%,drafts:create,%')
   AND (',' || scopes || ',') NOT LIKE '%,templates:read,%';

UPDATE api_clients
   SET scopes = scopes || ',accounts:read'
 WHERE ((',' || scopes || ',') LIKE '%,transactions:post,%'
        OR (',' || scopes || ',') LIKE '%,drafts:create,%')
   AND (',' || scopes || ',') NOT LIKE '%,accounts:read,%';
