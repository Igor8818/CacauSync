package br.gov.ceplac.cepec.cacausync.dto;

public record ProdutividadeCloneDto(
    String codigoClone,
    String descricaoClone,
    int totalPlantasCadastradas,
    int plantasVivasColetaveis,
    int plantasNaoColetaveisOuMortas,
    double taxaSobrevivenciaPercentual,
    int totalLeiturasRealizadas,
    int totalFrutosContados,
    double mediaFrutosPorPlantaViva
) {}
