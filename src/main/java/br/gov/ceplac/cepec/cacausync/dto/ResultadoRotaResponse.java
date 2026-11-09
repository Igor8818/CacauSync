package br.gov.ceplac.cepec.cacausync.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ResultadoRotaResponse(
    String codigoEnsaio,
    String origem,
    int totalPlantasRota,
    int plantasColetadas,
    boolean enviadoParaCelularUsb,
    String dispositivoUsb,
    String mensagem,
    LocalDateTime processadoEm,
    List<ItemRotaDto> itens
) {}
