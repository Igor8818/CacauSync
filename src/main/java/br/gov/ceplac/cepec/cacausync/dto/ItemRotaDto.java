package br.gov.ceplac.cepec.cacausync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ItemRotaDto(
    int ordem,
    String plantId,
    @JsonProperty("clone") String cloneCodigo,
    String parcela,
    int planta,
    int bloco,
    String situacao,
    boolean coletado,
    String qtdFrutos,
    Long leituraId,
    boolean temFoto,
    String statusValidacao
) {}
