-- ============================================================================
-- V4__fotos_e_validacao_leituras.sql
-- Foto de cada coleta + fluxo "Human-in-the-Loop": o pesquisador confere o que a IA
-- contou e valida, ajusta ou rejeita antes do dado entrar no relatorio oficial.
-- ============================================================================

ALTER TABLE leituras_coleta
    ADD COLUMN foto_chave VARCHAR(80),
    ADD COLUMN foto_content_type VARCHAR(40),
    ADD COLUMN status_validacao VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status_validacao IN ('PENDENTE', 'VALIDADA', 'AJUSTADA', 'REJEITADA')),
    ADD COLUMN qtd_frutos_validada INTEGER,
    ADD COLUMN observacao_validacao VARCHAR(500),
    ADD COLUMN validado_por VARCHAR(150),
    ADD COLUMN validado_em TIMESTAMP,
    ADD COLUMN deteccoes_json TEXT;

CREATE INDEX idx_leituras_status ON leituras_coleta(status_validacao);
