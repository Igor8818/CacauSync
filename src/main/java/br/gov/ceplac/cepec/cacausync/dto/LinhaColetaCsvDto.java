package br.gov.ceplac.cepec.cacausync.dto;

public record LinhaColetaCsvDto(
    String campanha,
    String plantId,
    String cloneCodigo,
    String numeroParcela,
    int numeroPlanta,
    String resultadoBruto,
    int qtdFrutos,
    boolean coletavel,
    String dataColeta,
    double latitude,
    double longitude
) {}
