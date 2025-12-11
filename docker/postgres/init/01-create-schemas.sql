CREATE SCHEMA IF NOT EXISTS histoflow;
CREATE SCHEMA IF NOT EXISTS audit;

-- Create enum type for tiling job status
CREATE TYPE tiling_job_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED');

-- initial table
CREATE TABLE IF NOT EXISTS histoflow.users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS histoflow.slides (
    id SERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    user_id INTEGER REFERENCES histoflow.users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);