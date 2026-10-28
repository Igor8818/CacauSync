-- ============================================================================
-- V3__update_default_users_password.sql
-- Atualiza as senhas dos usuarios padrao para o hash BCrypt exato de "cepec123"
-- ============================================================================

UPDATE usuarios
SET senha_hash = '$2a$10$qa0Tdcs.2MC089N8liZ9BeNpv3sN5v5RA3Dd/uFqk6NxHnb7W4dYm'
WHERE email IN ('coordenador@cepec.gov.br', 'pesquisador@cepec.gov.br');
