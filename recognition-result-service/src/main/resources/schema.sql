CREATE TABLE IF NOT EXISTS recognition_results (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(255) UNIQUE NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    question_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    recognition_type VARCHAR(50),
    recognized_text TEXT,
    operation_id VARCHAR(255),
    error_message TEXT,
    processed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_result_task_id ON recognition_results(task_id);
CREATE INDEX IF NOT EXISTS idx_result_user_id ON recognition_results(user_id);
CREATE INDEX IF NOT EXISTS idx_result_question_id ON recognition_results(question_id);
CREATE INDEX IF NOT EXISTS idx_result_status ON recognition_results(status);
CREATE INDEX IF NOT EXISTS idx_result_processed_at ON recognition_results(processed_at);

