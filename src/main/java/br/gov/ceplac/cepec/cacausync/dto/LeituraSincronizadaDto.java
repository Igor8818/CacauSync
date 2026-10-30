package br.gov.ceplac.cepec.cacausync.dto;

/**
 * Mapeamento entre o plantId gerado no CacauApp e o ID gerado pelo banco no CacauSync.
 * Usado pelo app Android para associar e enviar as fotos das leituras via POST /api/v1/leituras/{id}/foto.
 */
public record LeituraSincronizadaDto(
    String plantId,
    Long leituraId
) {}
