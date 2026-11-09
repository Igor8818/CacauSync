-- ============================================================================
-- V4__fotos_e_validacao_leituras.sql
-- Foto de cada coleta + fluxo "Human-in-the-Loop": o pesquisador confere o que a IA
-- contou e valida, ajusta ou rejeita antes do dado entrar no relatorio oficial.
-- ============================================================================

ALTER TABLE leituras_coleta ADD COLUMN foto_chave VARCHAR(80);
ALTER TABLE leituras_coleta ADD COLUMN foto_content_type VARCHAR(40);
ALTER TABLE leituras_coleta ADD COLUMN status_validacao VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
    CHECK (status_validacao IN ('PENDENTE', 'VALIDADA', 'AJUSTADA', 'REJEITADA'));
ALTER TABLE leituras_coleta ADD COLUMN qtd_frutos_validada INTEGER;
ALTER TABLE leituras_coleta ADD COLUMN observacao_validacao VARCHAR(500);
ALTER TABLE leituras_coleta ADD COLUMN validado_por VARCHAR(150);
ALTER TABLE leituras_coleta ADD COLUMN validado_em TIMESTAMP;
ALTER TABLE leituras_coleta ADD COLUMN deteccoes_json TEXT;

CREATE INDEX idx_leituras_status ON leituras_coleta(status_validacao);
