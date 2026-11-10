package br.gov.ceplac.cepec.cacausync.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "O usuário ou e-mail institucional é obrigatório")
    String email,

    @NotBlank(message = "A senha é obrigatória")
    String senha
) {}
