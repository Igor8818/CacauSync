package br.gov.ceplac.cepec.cacausync.dto;

/** Parâmetros usados na estimativa; devolvidos no relatório para o painel mostrar o que foi aplicado. */
public record ParametrosAgronomicosDto(
    double indiceFruto,
    int plantasPorHa,
    String campanha
) {
    public static final double INDICE_FRUTO_PADRAO = 22.0;
    public static final int PLANTAS_POR_HA_PADRAO = 1111;
}
