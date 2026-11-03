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
    int totalFrutosSadios,
    int totalFrutosVassoura,
    int totalFrutosPodridao,
    int totalFrutosNaoAvaliavel,
    double taxaInfeccaoPercentual,
    double mediaFrutosPorPlantaViva
) {
    /** Construtor retrocompatível de 9 argumentos. */
    public ProdutividadeCloneDto(
            String codigoClone, String descricaoClone, int totalPlantasCadastradas,
            int plantasVivasColetaveis, int plantasNaoColetaveisOuMortas, double taxaSobrevivenciaPercentual,
            int totalLeiturasRealizadas, int totalFrutosContados, double mediaFrutosPorPlantaViva
    ) {
        this(codigoClone, descricaoClone, totalPlantasCadastradas, plantasVivasColetaveis,
                plantasNaoColetaveisOuMortas, taxaSobrevivenciaPercentual, totalLeiturasRealizadas,
                totalFrutosContados, totalFrutosContados, 0, 0, 0, 0.0, mediaFrutosPorPlantaViva);
    }
}
