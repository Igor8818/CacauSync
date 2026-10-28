package br.gov.ceplac.cepec.cacausync.dto;

import java.time.LocalDateTime;

public record EnsaioResumoResponse(
    Long id,
    String codigo,
    String nome,
    String estacaoExperimental,
    String status,
    int totalPlantas,
    int totalLeituras,
    LocalDateTime criadoEm
) {}
