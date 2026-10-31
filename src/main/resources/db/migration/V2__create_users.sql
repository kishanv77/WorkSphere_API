CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_users_first_name_not_blank CHECK (char_length(btrim(first_name)) > 0),
    CONSTRAINT chk_users_last_name_not_blank CHECK (char_length(btrim(last_name)) > 0),
    CONSTRAINT chk_users_email_not_blank CHECK (char_length(btrim(email)) > 0),
    CONSTRAINT chk_users_password_hash_not_blank CHECK (char_length(btrim(password_hash)) > 0),
    CONSTRAINT chk_users_role CHECK (role = 'TENANT_ADMIN'),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
CREATE INDEX ix_users_tenant_id ON users (tenant_id);
CREATE INDEX ix_users_tenant_id_status ON users (tenant_id, status);

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
