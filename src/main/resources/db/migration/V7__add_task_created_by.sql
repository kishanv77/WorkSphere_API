ALTER TABLE tasks ADD COLUMN created_by_id UUID;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_created_by
        FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT;

CREATE INDEX ix_tasks_created_by_id ON tasks (created_by_id);