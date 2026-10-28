-- ============================================================================
-- V2__seed_ensaio_tc1801.sql
-- Dados iniciais reais extraídos da planilha de campo dados_cacau.csv (Ensaio TC-1801)
-- Senha padrão dos usuários de demonstração (BCrypt para "cepec123"):
-- $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- ============================================================================

INSERT INTO usuarios (nome, email, senha_hash, matricula_cepec, role) VALUES
('Dr. Coordenador Genética CEPEC', 'coordenador@cepec.gov.br', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'CEPEC-COORD-001', 'ROLE_COORDENADOR'),
('Pesquisador de Campo CEPEC', 'pesquisador@cepec.gov.br', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'CEPEC-PESQ-042', 'ROLE_PESQUISADOR');

INSERT INTO ensaios (codigo, nome, estacao_experimental, status) VALUES
('TC-1801', 'Ensaio Competitivo de Clones de Cacaueiro - Lote TC-1801', 'CEPEC/CEPLAC - Quadra Experimental Sul da Bahia', 'EM_ANDAMENTO');

INSERT INTO clones (codigo, descricao, origem_genetica) VALUES
('SCP-16005', 'Clone experimental de alta produtividade SCP-16005', 'Seleção CEPEC'),
('SCP-17202', 'Clone experimental tolerante a vassoura-de-bruxa SCP-17202', 'Seleção CEPEC'),
('SCP-17034', 'Clone experimental SCP-17034', 'Seleção CEPEC'),
('PS-13.19', 'Clone comercial Porto Seguro 13.19 (Padrão Produtividade)', 'Seleção Produtor / CEPEC'),
('CC-137', 'Clone Coleção CEPEC 137', 'Banco Ativo de Germoplasma CEPEC'),
('CCN-51', 'Colección Castro Naranjal 51 (Testemunha Internacional)', 'Introdução Híbrida');

-- Parcelas do Bloco 1 (Ensaio TC-1801)
INSERT INTO parcelas (ensaio_id, clone_id, bloco, numero_parcela) VALUES
((SELECT id FROM ensaios WHERE codigo = 'TC-1801'), (SELECT id FROM clones WHERE codigo = 'SCP-16005'), 1, '1'),
((SELECT id FROM ensaios WHERE codigo = 'TC-1801'), (SELECT id FROM clones WHERE codigo = 'SCP-17202'), 1, '2'),
((SELECT id FROM ensaios WHERE codigo = 'TC-1801'), (SELECT id FROM clones WHERE codigo = 'SCP-17034'), 1, '3'),
((SELECT id FROM ensaios WHERE codigo = 'TC-1801'), (SELECT id FROM clones WHERE codigo = 'PS-13.19'), 1, '13'),
((SELECT id FROM ensaios WHERE codigo = 'TC-1801'), (SELECT id FROM clones WHERE codigo = 'CC-137'), 1, '19'),
((SELECT id FROM ensaios WHERE codigo = 'TC-1801'), (SELECT id FROM clones WHERE codigo = 'CCN-51'), 1, '20');

-- Plantas reais do Ensaio TC-1801 (espelhando dados_cacau.csv)
-- Parcela 1: SCP-16005
INSERT INTO plantas (parcela_id, plant_code, numero_planta, ordem_rota, situacao, sit_pla_consolidado, sit_pla_historico) VALUES
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '1'), 'SCP-16005_TC-1801_par1_pla1_c0226', 1, 1, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '1'), 'SCP-16005_TC-1801_par1_pla2_c0226', 2, 2, 'NORMAL', 'N10_P0_E1_M0_V0', 'nnEnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '1'), 'SCP-16005_TC-1801_par1_pla3_c0226', 3, 3, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '1'), 'SCP-16005_TC-1801_par1_pla4_c0226', 4, 4, 'NAO_COLETAVEL', 'N0_P0_E0_M11_V0', 'MMMMMMMMMMM'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '1'), 'SCP-16005_TC-1801_par1_pla5_c0226', 5, 5, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),

-- Parcela 2: SCP-17202
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '2'), 'SCP-17202_TC-1801_par2_pla1_c0226', 1, 6, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '2'), 'SCP-17202_TC-1801_par2_pla2_c0226', 2, 7, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '2'), 'SCP-17202_TC-1801_par2_pla3_c0226', 3, 8, 'NAO_COLETAVEL', 'N0_P0_E0_M11_V0', 'MMMMMMMMMMM'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '2'), 'SCP-17202_TC-1801_par2_pla4_c0226', 4, 9, 'NAO_COLETAVEL', 'N0_P0_E0_M11_V0', 'MMMMMMMMMMM'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '2'), 'SCP-17202_TC-1801_par2_pla5_c0226', 5, 10, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),

-- Parcela 13: PS-13.19
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '13'), 'PS-13.19_TC-1801_par13_pla1_c0226', 1, 41, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '13'), 'PS-13.19_TC-1801_par13_pla2_c0226', 2, 42, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '13'), 'PS-13.19_TC-1801_par13_pla3_c0226', 3, 43, 'NORMAL', 'N10_P1_E0_M0_V0', 'nnPnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '13'), 'PS-13.19_TC-1801_par13_pla4_c0226', 4, 44, 'NAO_COLETAVEL', 'N0_P0_E0_M11_V0', 'MMMMMMMMMMM'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '13'), 'PS-13.19_TC-1801_par13_pla5_c0226', 5, 45, 'NORMAL', 'N4_P5_E0_M2_V0', 'PnPPnPMnPnM'),

-- Parcela 20: CCN-51
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '20'), 'CCN-51_TC-1801_par20_pla1_c0226', 1, 76, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '20'), 'CCN-51_TC-1801_par20_pla2_c0226', 2, 77, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '20'), 'CCN-51_TC-1801_par20_pla3_c0226', 3, 78, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '20'), 'CCN-51_TC-1801_par20_pla4_c0226', 4, 79, 'NAO_COLETAVEL', 'N0_P0_E0_M11_V0', 'MMMMMMMMMMM'),
((SELECT p.id FROM parcelas p JOIN ensaios e ON p.ensaio_id = e.id WHERE e.codigo = 'TC-1801' AND p.numero_parcela = '20'), 'CCN-51_TC-1801_par20_pla5_c0226', 5, 80, 'NORMAL', 'N11_P0_E0_M0_V0', 'nnnnnnnnnnn');
