-- Migração: adicionar tabela de tokens de recuperação de senha
-- Execute este arquivo se o schema_postgresql.sql já foi aplicado anteriormente.

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id_token SERIAL PRIMARY KEY,
    id_usuario INTEGER NOT NULL,
    token_hash VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_usuario ON password_reset_tokens(id_usuario);
