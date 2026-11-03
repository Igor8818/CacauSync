package br.gov.ceplac.cepec.cacausync.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RelatorioProdutividadeEnsaioResponse(
    String codigoEnsaio,
    String nomeEnsaio,
    String estacaoExperimental,
    int totalPlantasEnsaio,
    int totalPlantasVivas,
    int totalPlantasNaoColetaveis,
    int totalLeiturasRegistradas,
    int totalFrutosAcumulados,
    int totalFrutosSadios,
    int totalFrutosVassoura,
    int totalFrutosPodridao,
    int totalFrutosNaoAvaliavel,
    double taxaInfeccaoEnsaioPercentual,
    int leiturasComAlertaGps,
    int leiturasPendentesValidacao,
    int leiturasValidadas,
    int leiturasRejeitadas,
    LocalDateTime geradoEm,
    List<ProdutividadeCloneDto> rankingClones
) {
    /** Construtor retrocompatível de 14 argumentos. */
    public RelatorioProdutividadeEnsaioResponse(
            String codigoEnsaio, String nomeEnsaio, String estacaoExperimental,
            int totalPlantasEnsaio, int totalPlantasVivas, int totalPlantasNaoColetaveis,
            int totalLeiturasRegistradas, int totalFrutosAcumulados, int leiturasComAlertaGps,
            int leiturasPendentesValidacao, int leiturasValidadas, int leiturasRejeitadas,
            LocalDateTime geradoEm, List<ProdutividadeCloneDto> rankingClones
    ) {
        this(codigoEnsaio, nomeEnsaio, estacaoExperimental, totalPlantasEnsaio, totalPlantasVivas,
                totalPlantasNaoColetaveis, totalLeiturasRegistradas, totalFrutosAcumulados,
                totalFrutosAcumulados, 0, 0, 0, 0.0,
                leiturasComAlertaGps, leiturasPendentesValidacao, leiturasValidadas, leiturasRejeitadas,
                geradoEm, rankingClones);
    }
}
