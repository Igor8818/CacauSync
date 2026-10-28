package br.gov.ceplac.cepec.cacausync.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ResultadoImportacaoCsvResponse(
    String codigoEnsaio,
    String nomeArquivo,
    int totalLinhasProcessadas,
    int leiturasNovasInseridas,
    int leiturasDuplicadasIgnoradas,
    int plantasMarcadasNaoColetaveis,
    int alertasGpsZerado,
    int totalFrutosImportadosLote,
    LocalDateTime processadoEm,
    List<String> avisos
) {}
