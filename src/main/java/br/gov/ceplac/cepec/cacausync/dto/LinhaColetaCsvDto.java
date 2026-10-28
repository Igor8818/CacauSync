package br.gov.ceplac.cepec.cacausync.dto;

import java.util.List;

/**
 * Uma linha de coleta ja "traduzida" do CSV (ou do JSON) do aplicativo.
 * "deteccoes" so vem preenchida pelo caminho JSON (o CSV atual do app nao leva as caixas do YOLO).
 */
public record LinhaColetaCsvDto(
    String campanha,
    String plantId,
    String cloneCodigo,
    String numeroParcela,
    int numeroPlanta,
    String resultadoBruto,
    int qtdFrutos,
    boolean coletavel,
    String dataColeta,
    double latitude,
    double longitude,
    List<DeteccaoDto> deteccoes
) {
    /** Construtor usado pelo parser de CSV: sem caixas do YOLO. */
    public LinhaColetaCsvDto(
            String campanha, String plantId, String cloneCodigo, String numeroParcela, int numeroPlanta,
            String resultadoBruto, int qtdFrutos, boolean coletavel, String dataColeta,
            double latitude, double longitude
    ) {
        this(campanha, plantId, cloneCodigo, numeroParcela, numeroPlanta,
                resultadoBruto, qtdFrutos, coletavel, dataColeta, latitude, longitude, List.of());
    }
}
