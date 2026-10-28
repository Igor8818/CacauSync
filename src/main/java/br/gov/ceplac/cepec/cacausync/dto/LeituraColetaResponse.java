package br.gov.ceplac.cepec.cacausync.dto;

import br.gov.ceplac.cepec.cacausync.domain.entity.StatusValidacao;

import java.time.LocalDateTime;
import java.util.List;

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
    LocalDateTime importadoEm,
    boolean temFoto,
    StatusValidacao statusValidacao,
    Integer qtdFrutosValidada,
    int qtdEfetiva,
    String observacaoValidacao,
    String validadoPor,
    LocalDateTime validadoEm,
    List<DeteccaoDto> deteccoes
) {}
