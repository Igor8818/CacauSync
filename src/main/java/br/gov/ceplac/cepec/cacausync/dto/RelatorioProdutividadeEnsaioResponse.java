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
    int leiturasComAlertaGps,
    LocalDateTime geradoEm,
    List<ProdutividadeCloneDto> rankingClones
) {}
