CREATE TABLE IF NOT EXISTS recognition_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(255) UNIQUE NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    question_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    recognition_type VARCHAR(50),
    recognized_text TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_id ON recognition_tasks(task_id);
CREATE INDEX IF NOT EXISTS idx_user_id ON recognition_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_status ON recognition_tasks(status);
CREATE INDEX IF NOT EXISTS idx_created_at ON recognition_tasks(created_at);

