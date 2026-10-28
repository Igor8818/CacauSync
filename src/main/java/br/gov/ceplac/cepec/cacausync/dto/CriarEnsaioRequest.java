package br.gov.ceplac.cepec.cacausync.dto;

import jakarta.validation.constraints.NotBlank;

public record CriarEnsaioRequest(
    @NotBlank(message = "O código do ensaio é obrigatório (ex: TC-1801)")
    String codigo,

    @NotBlank(message = "O nome do ensaio é obrigatório")
    String nome,

    String estacaoExperimental
) {}
