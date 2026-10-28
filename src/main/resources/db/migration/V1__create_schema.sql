-- ============================================================================
-- V1__create_schema.sql
-- Modelagem Relacional de Ensaios Genéticos e Coletas de Campo (CEPEC/CEPLAC)
-- Hierarquia: Ensaio -> Parcela (vinculada a Clone) -> Planta -> LeituraColeta
-- ============================================================================

CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    matricula_cepec VARCHAR(40) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ROLE_COORDENADOR', 'ROLE_PESQUISADOR')),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ensaios (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nome VARCHAR(160) NOT NULL,
    estacao_experimental VARCHAR(120) NOT NULL DEFAULT 'CEPEC/CEPLAC - Ilhéus/Itabuna, BA',
    status VARCHAR(30) NOT NULL DEFAULT 'EM_ANDAMENTO',
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE clones (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    descricao VARCHAR(200),
    origem_genetica VARCHAR(100) DEFAULT 'Programa de Melhoramento Genético CEPEC'
);

CREATE TABLE parcelas (
    id BIGSERIAL PRIMARY KEY,
    ensaio_id BIGINT NOT NULL REFERENCES ensaios(id) ON DELETE CASCADE,
    clone_id BIGINT NOT NULL REFERENCES clones(id),
    bloco INTEGER NOT NULL DEFAULT 1,
    numero_parcela VARCHAR(30) NOT NULL,
    CONSTRAINT uk_ensaio_parcela UNIQUE (ensaio_id, numero_parcela)
);

CREATE TABLE plantas (
    id BIGSERIAL PRIMARY KEY,
    parcela_id BIGINT NOT NULL REFERENCES parcelas(id) ON DELETE CASCADE,
    plant_code VARCHAR(120) NOT NULL UNIQUE,
    numero_planta INTEGER NOT NULL,
    ordem_rota INTEGER NOT NULL,
    situacao VARCHAR(30) NOT NULL DEFAULT 'NORMAL' CHECK (situacao IN ('NORMAL', 'NAO_COLETAVEL')),
    sit_pla_consolidado VARCHAR(40) DEFAULT 'N11_P0_E0_M0_V0',
    sit_pla_historico VARCHAR(40) DEFAULT 'nnnnnnnnnnn',
    CONSTRAINT uk_parcela_planta UNIQUE (parcela_id, numero_planta)
);

CREATE TABLE leituras_coleta (
    id BIGSERIAL PRIMARY KEY,
    planta_id BIGINT NOT NULL REFERENCES plantas(id) ON DELETE CASCADE,
    pesquisador_email VARCHAR(150),
    campanha VARCHAR(80) NOT NULL,
    qtd_frutos INTEGER NOT NULL DEFAULT 0,
    coletavel BOOLEAN NOT NULL DEFAULT TRUE,
    diagnostico_bruto VARCHAR(80) NOT NULL,
    data_coleta_str VARCHAR(40) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    longitude DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    alerta_gps BOOLEAN NOT NULL DEFAULT FALSE,
    chave_idempotencia VARCHAR(200) NOT NULL UNIQUE,
    importado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_parcelas_ensaio ON parcelas(ensaio_id);
CREATE INDEX idx_parcelas_clone ON parcelas(clone_id);
CREATE INDEX idx_plantas_parcela ON plantas(parcela_id);
CREATE INDEX idx_leituras_planta ON leituras_coleta(planta_id);
CREATE INDEX idx_leituras_campanha ON leituras_coleta(campanha);
