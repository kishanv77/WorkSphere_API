CREATE TABLE task_comments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL,
    author_id UUID NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_task_comments_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_comments_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_comments_author
        FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_task_comments_body_not_blank
        CHECK (char_length(btrim(body)) > 0)
);

CREATE INDEX ix_task_comments_tenant_id ON task_comments (tenant_id);
CREATE INDEX ix_task_comments_task_id_created_at ON task_comments (task_id, created_at);

CREATE TRIGGER trg_task_comments_set_updated_at
BEFORE UPDATE ON task_comments
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();