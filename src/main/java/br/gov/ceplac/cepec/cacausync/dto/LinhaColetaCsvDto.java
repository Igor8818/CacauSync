package br.gov.ceplac.cepec.cacausync.dto;

import java.util.List;

/**
 * Uma linha de coleta traduzida do CSV ou do JSON do aplicativo,
 * incluindo a estratificação fitossanitária (Sadios, Vassoura, Podridão, Não Avaliável) e as caixas do YOLO.
 */
public record LinhaColetaCsvDto(
    String campanha,
    String plantId,
    String cloneCodigo,
    String numeroParcela,
    int numeroPlanta,
    String resultadoBruto,
    int qtdFrutos,
    int qtdSadios,
    int qtdVassoura,
    int qtdPodridao,
    int qtdNaoAvaliavel,
    boolean coletavel,
    String dataColeta,
    double latitude,
    double longitude,
    List<DeteccaoDto> deteccoes
) {
    /** Construtor retrocompatível (12 argumentos: com detecções, sem divisão explícita de doenças). */
    public LinhaColetaCsvDto(
            String campanha, String plantId, String cloneCodigo, String numeroParcela, int numeroPlanta,
            String resultadoBruto, int qtdFrutos, boolean coletavel, String dataColeta,
            double latitude, double longitude, List<DeteccaoDto> deteccoes
    ) {
        this(
                campanha, plantId, cloneCodigo, numeroParcela, numeroPlanta,
                resultadoBruto, qtdFrutos,
                coletavel ? contarSadiosOuTotal(qtdFrutos, deteccoes) : 0,
                coletavel ? contarPorClasse(deteccoes, "vassoura") : 0,
                coletavel ? contarPorClasse(deteccoes, "podridao", "podridão", "monilia", "monília") : 0,
                coletavel ? contarPorClasse(deteccoes, "nao_avaliavel", "não avaliável", "nao avaliavel") : 0,
                coletavel, dataColeta, latitude, longitude,
                deteccoes != null ? deteccoes : List.of()
        );
    }

    /** Construtor retrocompatível (11 argumentos: sem caixas do YOLO). */
    public LinhaColetaCsvDto(
            String campanha, String plantId, String cloneCodigo, String numeroParcela, int numeroPlanta,
            String resultadoBruto, int qtdFrutos, boolean coletavel, String dataColeta,
            double latitude, double longitude
    ) {
        this(campanha, plantId, cloneCodigo, numeroParcela, numeroPlanta,
                resultadoBruto, qtdFrutos, coletavel ? qtdFrutos : 0, 0, 0, 0,
                coletavel, dataColeta, latitude, longitude, List.of());
    }

    private static int contarSadiosOuTotal(int qtdFrutos, List<DeteccaoDto> deteccoes) {
        if (deteccoes == null || deteccoes.isEmpty()) {
            return Math.max(0, qtdFrutos);
        }
        int v = contarPorClasse(deteccoes, "vassoura");
        int p = contarPorClasse(deteccoes, "podridao", "podridão", "monilia", "monília");
        int na = contarPorClasse(deteccoes, "nao_avaliavel", "não avaliável", "nao avaliavel");
        int s = contarPorClasse(deteccoes, "sadio", "cacau_sadio", "fruto_maduro", "fruto_verde", "cacau", "fruto");
        return s > 0 ? s : Math.max(0, deteccoes.size() - v - p - na);
    }

    private static int contarPorClasse(List<DeteccaoDto> deteccoes, String... termos) {
        if (deteccoes == null || deteccoes.isEmpty()) return 0;
        int c = 0;
        for (DeteccaoDto d : deteccoes) {
            if (d == null || d.classe() == null) continue;
            String norm = d.classe().toLowerCase();
            for (String t : termos) {
                if (norm.contains(t)) {
                    c++;
                    break;
                }
            }
        }
        return c;
    }
}
