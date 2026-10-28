package br.gov.ceplac.cepec.cacausync.domain.entity;

/**
 * Ciclo de vida de uma leitura de campo diante do pesquisador:
 * PENDENTE  -> acabou de chegar do celular, ninguem conferiu;
 * VALIDADA  -> pesquisador concordou com a contagem da IA;
 * AJUSTADA  -> pesquisador corrigiu a contagem (vale o numero dele);
 * REJEITADA -> foto ruim/dado invalido, fica de fora do relatorio.
 */
public enum StatusValidacao {
    PENDENTE,
    VALIDADA,
    AJUSTADA,
    REJEITADA
}
