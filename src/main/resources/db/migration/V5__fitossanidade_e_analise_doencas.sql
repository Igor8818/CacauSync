-- ============================================================================
-- V5__fitossanidade_e_analise_doencas.sql
-- Contagem detalhada por classe fitossanitária (Sadios, Vassoura de Bruxa,
-- Podridão Parda/Monília e Não Avaliável) extraída das planilhas e do YOLO.
-- ============================================================================

ALTER TABLE leituras_coleta ADD COLUMN qtd_sadios INTEGER NOT NULL DEFAULT 0;
ALTER TABLE leituras_coleta ADD COLUMN qtd_vassoura INTEGER NOT NULL DEFAULT 0;
ALTER TABLE leituras_coleta ADD COLUMN qtd_podridao INTEGER NOT NULL DEFAULT 0;
ALTER TABLE leituras_coleta ADD COLUMN qtd_nao_avaliavel INTEGER NOT NULL DEFAULT 0;
