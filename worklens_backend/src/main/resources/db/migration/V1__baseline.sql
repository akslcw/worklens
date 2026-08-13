CREATE TABLE IF NOT EXISTS employees (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    employee_no VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    employee_id BIGINT REFERENCES employees(id),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS employee_id BIGINT;

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE auth_users au
SET employee_id = NULL
WHERE employee_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM employees e
      WHERE e.id = au.employee_id
  );

ALTER TABLE auth_users
    DROP CONSTRAINT IF EXISTS auth_users_employee_id_fkey;

ALTER TABLE auth_users
    ADD CONSTRAINT auth_users_employee_id_fkey
    FOREIGN KEY (employee_id) REFERENCES employees(id);

ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS auth_login_attempts (
    username VARCHAR(100) PRIMARY KEY,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS usage_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    app_name VARCHAR(100) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS detail_access_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_employee_id BIGINT NOT NULL REFERENCES employees(id),
    target_employee_id BIGINT NOT NULL REFERENCES employees(id),
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NULL,
    processed_by_employee_id BIGINT NULL REFERENCES employees(id)
);

CREATE TABLE IF NOT EXISTS detail_access_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    detail_access_request_id BIGINT NOT NULL REFERENCES detail_access_requests(id) ON DELETE CASCADE,
    viewer_employee_id BIGINT NOT NULL REFERENCES employees(id),
    target_employee_id BIGINT NOT NULL REFERENCES employees(id),
    viewed_at TIMESTAMP NOT NULL
);

ALTER TABLE usage_records
    ADD COLUMN IF NOT EXISTS employee_id BIGINT;

ALTER TABLE usage_records
    DROP CONSTRAINT IF EXISTS usage_records_auth_user_id_fkey;

ALTER TABLE usage_records
    DROP COLUMN IF EXISTS auth_user_id;

ALTER TABLE usage_records
    ALTER COLUMN employee_id SET NOT NULL;

ALTER TABLE usage_records
    DROP CONSTRAINT IF EXISTS usage_records_employee_id_fkey;

ALTER TABLE usage_records
    ADD CONSTRAINT usage_records_employee_id_fkey
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

ALTER TABLE usage_records
    ADD COLUMN IF NOT EXISTS client_record_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_usage_records_employee_client_record
    ON usage_records (employee_id, client_record_id)
    WHERE client_record_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS llm_reports (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    requester_employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    target_employee_id BIGINT NULL REFERENCES employees(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    period_started_at TIMESTAMP NULL,
    period_ended_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS report_scope VARCHAR(20);

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS period_type VARCHAR(20);

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS period_start_date DATE;

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS period_end_date DATE;

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS detail_json JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS source_layer VARCHAR(30);

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS source_count INTEGER;

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS generated_at TIMESTAMP;

ALTER TABLE llm_reports
    ADD COLUMN IF NOT EXISTS source_record_ids BIGINT[];

ALTER TABLE llm_reports
    ALTER COLUMN requester_employee_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_llm_reports_employee_period
    ON llm_reports (report_scope, period_type, target_employee_id, period_start_date, period_end_date)
    WHERE report_scope = 'EMPLOYEE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_llm_reports_team_period
    ON llm_reports (report_scope, period_type, period_start_date, period_end_date)
    WHERE report_scope = 'TEAM';
