package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
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
 * Parser RFC 4180 compatível com os dois formatos reais de exportação CSV do aplicativo Android CacauApp:
 * 1) exportarCSVCEPEC_Personalizado (MainActivity.kt): Campanha,ID_Gerado,Clone,Parcela,Planta,Resultado,Data,Lat,Lng
 * 2) exportarCSVFinal (FileUtils.kt): Ensaio,Parcela,Plant_ID,Diagnostico,Data,Lat,Lng
 */
@Component
public class CsvColetaParser {

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
                        "Cabeçalho CSV inválido. Esperado formato do CacauApp (7 ou 9 colunas), encontrado: " + colunasCabecalho.size()
                );
            }

            boolean formatoPersonalizado9Colunas = colunasCabecalho.size() >= 9
                    || colunasCabecalho.get(0).equalsIgnoreCase("Campanha");

            String linha;
            int numeroLinha = 1;
            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                String linhaTrim = removerBomUtf8(linha).trim();
                if (linhaTrim.isEmpty()) {
                    continue;
                }

                List<String> cols = fatiarLinhaRfc4180(linhaTrim);
                if (formatoPersonalizado9Colunas && cols.size() >= 9) {
                    resultado.add(mapearFormatoPersonalizado(cols));
                } else if (cols.size() >= 7) {
                    resultado.add(mapearFormatoFinal7Colunas(cols));
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

    private LinhaColetaCsvDto mapearFormatoPersonalizado(List<String> cols) {
        // Campanha(0), ID_Gerado(1), Clone(2), Parcela(3), Planta(4), Resultado(5), Data(6), Lat(7), Lng(8)
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
        int qtdFrutos = coletavel ? extrairInteiroSeguro(resultadoBruto, 0) : 0;

        return new LinhaColetaCsvDto(
                campanha, plantId, clone, parcela, numeroPlanta,
                resultadoBruto, qtdFrutos, coletavel, data, lat, lng
        );
    }

    private LinhaColetaCsvDto mapearFormatoFinal7Colunas(List<String> cols) {
        // Ensaio(0), Parcela(1), Plant_ID(2), Diagnostico(3), Data(4), Lat(5), Lng(6)
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
        int qtdFrutos = coletavel ? extrairInteiroSeguro(diagnosticoBruto, 0) : 0;

        return new LinhaColetaCsvDto(
                campanha, plantId, clone, parcela, numeroPlanta,
                diagnosticoBruto, qtdFrutos, coletavel, data, lat, lng
        );
    }

    public boolean isPlantaColetavel(String diagnostico) {
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
            if (c == '"') {
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

    private int extrairInteiroSeguro(String valor, int padrao) {
        if (valor == null || valor.isBlank()) return padrao;
        String apenasDigitos = valor.replaceAll("[^0-9-]", "");
        if (apenasDigitos.isEmpty()) return padrao;
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
