package br.gov.ceplac.cepec.cacausync.dto;

import java.time.LocalDateTime;

public record LeituraColetaResponse(
    Long id,
    String codigoEnsaio,
    String campanha,
    String plantCode,
    String codigoClone,
    String numeroParcela,
    int numeroPlanta,
    int qtdFrutos,
    boolean coletavel,
    String diagnosticoBruto,
    String dataColeta,
    double latitude,
    double longitude,
    boolean alertaGps,
    String pesquisadorEmail,
    LocalDateTime importadoEm
) {}
