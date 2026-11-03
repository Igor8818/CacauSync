package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.dto.DeteccaoDto;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser RFC 4180 compatível com os três formatos de planilha do ecossistema CEPEC/CacauApp:
 * 1) exportarCSVCEPEC_Personalizado (MainActivity.kt): 9, 14 ou 15 colunas (com Sadios, Vassoura, Podridao, Nao_Avaliavel, Deteccoes_YOLO)
 * 2) exportarCSVFinal (FileUtils.kt): 7, 12 ou 13 colunas
 * 3) Planilha estrutural FieldBook CEPEC (dados_cacau.csv): plant_id,Ensaio,Bloco,Parcela,Planta,Clone,Ordem,SitPla2023c,SitPla2023L
 */
@Component
public class CsvColetaParser {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private enum TipoPlanilha {
        COLETA_PERSONALIZADA,
        COLETA_FINAL,
        FIELDBOOK_ESTRUTURAL
    }

    public List<LinhaColetaCsvDto> parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new RegraNegocioException("O arquivo CSV enviado está nulo ou inacessível.");
        }

        List<LinhaColetaCsvDto> resultado = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String cabecalhoRaw = reader.readLine();
            if (cabecalhoRaw == null || cabecalhoRaw.isBlank()) {
                throw new RegraNegocioException("A planilha CSV enviada está vazia.");
            }

            String cabecalhoLimpo = removerBomUtf8(cabecalhoRaw).trim();
            List<String> colunasCabecalho = fatiarLinhaRfc4180(cabecalhoLimpo);

            if (colunasCabecalho.size() < 7) {
                throw new RegraNegocioException(
                        "Cabeçalho CSV inválido. Esperado formato do CacauApp (mínimo 7 colunas), encontrado: " + colunasCabecalho.size()
                );
            }

            TipoPlanilha tipo = detectarTipoPlanilha(colunasCabecalho);

            String linha;
            int numeroLinha = 1;
            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                String linhaTrim = removerBomUtf8(linha).trim();
                if (linhaTrim.isEmpty()) {
                    continue;
                }

                List<String> cols = fatiarLinhaRfc4180(linhaTrim);
                if (tipo == TipoPlanilha.FIELDBOOK_ESTRUTURAL && cols.size() >= 7) {
                    resultado.add(mapearFormatoFieldBookEstrutural(cols));
                } else if (tipo == TipoPlanilha.COLETA_PERSONALIZADA && cols.size() >= 9) {
                    resultado.add(mapearFormatoPersonalizado(cols));
                } else if (cols.size() >= 7) {
                    resultado.add(mapearFormatoFinal(cols));
                } else {
                    throw new RegraNegocioException("Linha " + numeroLinha + " possui número insuficiente de colunas: " + linhaTrim);
                }
            }
        } catch (IOException e) {
            throw new RegraNegocioException("Falha ao ler o arquivo CSV de coleta: " + e.getMessage());
        }

        if (resultado.isEmpty()) {
            throw new RegraNegocioException("O arquivo CSV contém apenas o cabeçalho e nenhum registro de coleta.");
        }

        return resultado;
    }

    private TipoPlanilha detectarTipoPlanilha(List<String> cabecalho) {
        String c0 = cabecalho.get(0).trim().toLowerCase(Locale.ROOT);
        boolean temSitPla = cabecalho.stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).startsWith("sitpla"));
        boolean temOrdem = cabecalho.stream().anyMatch(c -> c.trim().equalsIgnoreCase("ordem"));
        if ((c0.equals("plant_id") || temSitPla) && temOrdem) {
            return TipoPlanilha.FIELDBOOK_ESTRUTURAL;
        }
        if (c0.equalsIgnoreCase("campanha") || (cabecalho.size() >= 9 && !c0.equalsIgnoreCase("ensaio"))) {
            return TipoPlanilha.COLETA_PERSONALIZADA;
        }
        if (c0.equalsIgnoreCase("ensaio")) {
            return TipoPlanilha.COLETA_FINAL;
        }
        return cabecalho.size() >= 9 ? TipoPlanilha.COLETA_PERSONALIZADA : TipoPlanilha.COLETA_FINAL;
    }

    private LinhaColetaCsvDto mapearFormatoPersonalizado(List<String> cols) {
        // 0:Campanha, 1:ID_Gerado, 2:Clone, 3:Parcela, 4:Planta, 5:Resultado, 6:Data, 7:Lat, 8:Lng
        // Opcionais: 9:Total_Frutos, 10:Sadios, 11:Vassoura, 12:Podridao, 13:Nao_Avaliavel, 14:Deteccoes_YOLO
        String campanha = cols.get(0).trim();
        String plantId = cols.get(1).trim();
        String clone = cols.get(2).trim().isEmpty() ? extrairCloneDePlantId(plantId) : cols.get(2).trim();
        String parcela = cols.get(3).trim();
        int numeroPlanta = extrairInteiroSeguro(cols.get(4), 1);
        String resultadoBruto = cols.get(5).trim();
        String data = cols.get(6).trim();
        double lat = extrairDoubleSeguro(cols.get(7));
        double lng = extrairDoubleSeguro(cols.get(8));

        boolean coletavel = isPlantaColetavel(resultadoBruto);
        int qtdResultado = coletavel ? extrairInteiroSeguro(resultadoBruto, 0) : 0;
        int totalCol = cols.size() > 9 ? extrairInteiroSeguro(cols.get(9), qtdResultado) : qtdResultado;
        int qtdFrutos = coletavel ? Math.max(qtdResultado, totalCol) : 0;

        List<DeteccaoDto> deteccoes = cols.size() > 14 ? parseDeteccoesJson(cols.get(14)) : List.of();

        int sadios;
        int vassoura = 0;
        int podridao = 0;
        int naoAvaliavel = 0;

        if (!coletavel) {
            sadios = 0;
        } else if (cols.size() >= 14) {
            sadios = extrairInteiroSeguro(cols.get(10), 0);
            vassoura = extrairInteiroSeguro(cols.get(11), 0);
            podridao = extrairInteiroSeguro(cols.get(12), 0);
            naoAvaliavel = extrairInteiroSeguro(cols.get(13), 0);
            if (sadios + vassoura + podridao + naoAvaliavel == 0 && qtdFrutos > 0) {
                sadios = qtdFrutos;
            }
        } else if (!deteccoes.isEmpty()) {
            LinhaColetaCsvDto temp = new LinhaColetaCsvDto(
                    campanha, plantId, clone, parcela, numeroPlanta,
                    resultadoBruto, qtdFrutos, true, data, lat, lng, deteccoes
            );
            return temp;
        } else {
            sadios = qtdFrutos;
        }

        return new LinhaColetaCsvDto(
                campanha, plantId, clone, parcela, numeroPlanta,
                resultadoBruto, qtdFrutos, sadios, vassoura, podridao, naoAvaliavel,
                coletavel, data, lat, lng, deteccoes
        );
    }

    private LinhaColetaCsvDto mapearFormatoFinal(List<String> cols) {
        // 0:Ensaio, 1:Parcela, 2:Plant_ID, 3:Diagnostico, 4:Data, 5:Lat, 6:Lng
        // Opcionais: 7:Total_Frutos, 8:Sadios, 9:Vassoura, 10:Podridao, 11:Nao_Avaliavel, 12:Deteccoes_YOLO
        String campanha = cols.get(0).trim();
        String parcela = cols.get(1).trim();
        String plantId = cols.get(2).trim();
        String diagnosticoBruto = cols.get(3).trim();
        String data = cols.get(4).trim();
        double lat = extrairDoubleSeguro(cols.get(5));
        double lng = extrairDoubleSeguro(cols.get(6));

        String clone = extrairCloneDePlantId(plantId);
        int numeroPlanta = extrairPlantaDePlantId(plantId);
        boolean coletavel = isPlantaColetavel(diagnosticoBruto);
        int qtdDiag = coletavel ? extrairInteiroSeguro(diagnosticoBruto, 0) : 0;
        int totalCol = cols.size() > 7 ? extrairInteiroSeguro(cols.get(7), qtdDiag) : qtdDiag;
        int qtdFrutos = coletavel ? Math.max(qtdDiag, totalCol) : 0;

        List<DeteccaoDto> deteccoes = cols.size() > 12 ? parseDeteccoesJson(cols.get(12)) : List.of();

        int sadios = coletavel ? qtdFrutos : 0;
        int vassoura = 0;
        int podridao = 0;
        int naoAvaliavel = 0;

        if (coletavel && cols.size() >= 12) {
            sadios = extrairInteiroSeguro(cols.get(8), 0);
            vassoura = extrairInteiroSeguro(cols.get(9), 0);
            podridao = extrairInteiroSeguro(cols.get(10), 0);
            naoAvaliavel = extrairInteiroSeguro(cols.get(11), 0);
            if (sadios + vassoura + podridao + naoAvaliavel == 0 && qtdFrutos > 0) {
                sadios = qtdFrutos;
            }
        }

        return new LinhaColetaCsvDto(
                campanha, plantId, clone, parcela, numeroPlanta,
                diagnosticoBruto, qtdFrutos, sadios, vassoura, podridao, naoAvaliavel,
                coletavel, data, lat, lng, deteccoes
        );
    }

    private LinhaColetaCsvDto mapearFormatoFieldBookEstrutural(List<String> cols) {
        // 0:plant_id, 1:Ensaio, 2:Bloco, 3:Parcela, 4:Planta, 5:Clone, 6:Ordem, 7:SitPla2023c, 8:SitPla2023L
        String plantId = cols.get(0).trim();
        String ensaio = cols.get(1).trim().isEmpty() ? "TC-1801" : cols.get(1).trim();
        String parcela = cols.get(3).trim();
        int numeroPlanta = extrairInteiroSeguro(cols.get(4), 1);
        String clone = cols.get(5).trim().isEmpty() ? extrairCloneDePlantId(plantId) : cols.get(5).trim();
        String sitConsolidado = cols.size() > 7 ? cols.get(7).trim() : "N11_P0_E0_M0_V0";
        String sitHistorico = cols.size() > 8 ? cols.get(8).trim() : "";

        boolean morta = sitConsolidado.toUpperCase(Locale.ROOT).startsWith("N0_")
                || sitHistorico.toUpperCase(Locale.ROOT).endsWith("MMM");
        boolean coletavel = !morta;
        String resultadoBruto = morta ? "Não Coletável (" + sitConsolidado + ")" : "0";

        return new LinhaColetaCsvDto(
                ensaio, plantId, clone, parcela, numeroPlanta,
                resultadoBruto, 0, 0, 0, 0, 0,
                coletavel, "CADASTRO_ENSAIO", 0.0, 0.0, List.of()
        );
    }

    private List<DeteccaoDto> parseDeteccoesJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("[")) {
            return List.of();
        }
        try {
            return objectMapper.readValue(trimmed, new TypeReference<List<DeteccaoDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public static boolean isPlantaColetavel(String diagnostico) {
        if (diagnostico == null || diagnostico.isBlank()) {
            return false;
        }
        String norm = diagnostico.toLowerCase(Locale.ROOT).trim();
        return !norm.contains("não coletável")
                && !norm.contains("nao coletavel")
                && !norm.contains("morta")
                && !norm.contains("seca")
                && !norm.contains("ausente");
    }

    public List<String> fatiarLinhaRfc4180(String linha) {
        List<String> campos = new ArrayList<>();
        if (linha == null || linha.isEmpty()) {
            return campos;
        }

        char separador = detectarSeparadorForaDeAspas(linha);
        StringBuilder atual = new StringBuilder();
        boolean dentroDeAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '\\' && dentroDeAspas && i + 1 < linha.length() && (linha.charAt(i + 1) == '"' || linha.charAt(i + 1) == '\\')) {
                atual.append(linha.charAt(i + 1));
                i++;
            } else if (c == '"') {
                if (dentroDeAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    dentroDeAspas = !dentroDeAspas;
                }
            } else if (c == separador && !dentroDeAspas) {
                campos.add(atual.toString().trim());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString().trim());
        return campos;
    }

    private char detectarSeparadorForaDeAspas(String linha) {
        boolean emAspas = false;
        int virgulas = 0;
        int pontoEVirgulas = 0;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                emAspas = !emAspas;
            } else if (!emAspas) {
                if (c == ',') virgulas++;
                else if (c == ';') pontoEVirgulas++;
            }
        }
        return pontoEVirgulas > virgulas ? ';' : ',';
    }

    private String removerBomUtf8(String texto) {
        if (texto == null) return "";
        return texto.replace("\uFEFF", "").replace("ï»¿", "");
    }

    private String extrairCloneDePlantId(String plantId) {
        if (plantId == null || plantId.isBlank()) return "CLONE-DESCONHECIDO";
        String[] partes = plantId.split("_");
        return partes.length > 0 && !partes[0].isBlank() ? partes[0].trim() : plantId;
    }

    private int extrairPlantaDePlantId(String plantId) {
        if (plantId == null) return 1;
        String[] partes = plantId.split("_");
        for (int i = partes.length - 1; i >= 0; i--) {
            String digitos = partes[i].replaceAll("\\D+", "");
            if (!digitos.isEmpty() && (partes[i].toLowerCase(Locale.ROOT).startsWith("pl") || i == partes.length - 1)) {
                return extrairInteiroSeguro(digitos, 1);
            }
        }
        return 1;
    }

    public static int extrairQuantidade(String valor) {
        return extrairInteiroSeguro(valor, 0);
    }

    private static int extrairInteiroSeguro(String valor, int padrao) {
        if (valor == null || valor.isBlank()) return padrao;
        String apenasDigitos = valor.replaceAll("[^0-9-]", "");
        if (apenasDigitos.isEmpty() || apenasDigitos.equals("-")) return padrao;
        try {
            return Math.max(0, Integer.parseInt(apenasDigitos));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private double extrairDoubleSeguro(String valor) {
        if (valor == null || valor.isBlank()) return 0.0;
        try {
            return Double.parseDouble(valor.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
