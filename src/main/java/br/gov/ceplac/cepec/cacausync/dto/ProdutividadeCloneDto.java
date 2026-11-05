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
    double mediaFrutosPorPlantaViva,
    int plantasAvaliadas,
    double coberturaPercentual,
    EstimativaAgronomicaDto estimativa
) {
    /** Construtor retrocompatível de 14 argumentos (sem cobertura nem estimativa). */
    public ProdutividadeCloneDto(
            String codigoClone, String descricaoClone, int totalPlantasCadastradas,
            int plantasVivasColetaveis, int plantasNaoColetaveisOuMortas, double taxaSobrevivenciaPercentual,
            int totalLeiturasRealizadas, int totalFrutosContados, int totalFrutosSadios,
            int totalFrutosVassoura, int totalFrutosPodridao, int totalFrutosNaoAvaliavel,
            double taxaInfeccaoPercentual, double mediaFrutosPorPlantaViva
    ) {
        this(codigoClone, descricaoClone, totalPlantasCadastradas, plantasVivasColetaveis,
                plantasNaoColetaveisOuMortas, taxaSobrevivenciaPercentual, totalLeiturasRealizadas,
                totalFrutosContados, totalFrutosSadios, totalFrutosVassoura, totalFrutosPodridao,
                totalFrutosNaoAvaliavel, taxaInfeccaoPercentual, mediaFrutosPorPlantaViva,
                0, 0.0, EstimativaAgronomicaDto.zerada());
    }

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
