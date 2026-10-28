package br.gov.ceplac.cepec.cacausync.dto;

import br.gov.ceplac.cepec.cacausync.domain.entity.StatusValidacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ValidarLeituraRequest(
    @NotNull(message = "Informe o status: VALIDADA, AJUSTADA, REJEITADA ou PENDENTE")
    StatusValidacao status,

    @PositiveOrZero(message = "A contagem ajustada nao pode ser negativa")
    Integer qtdFrutosValidada,

    @Size(max = 500, message = "A observacao aceita no maximo 500 caracteres")
    String observacao
) {}
