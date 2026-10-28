package br.gov.ceplac.cepec.cacausync.dto;

public record TokenResponse(
    String token,
    String tipo,
    String nomePesquisador,
    String email,
    String role,
    long expiraEmSegundos
) {}
