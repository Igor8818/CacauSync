package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.dto.ArquivoFoto;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descobre qual foto pertence a qual linha do CSV, usando so o NOME do arquivo.
 *
 * O app salva a foto como "<ID_Gerado>_<milissegundos>.jpg" (ver tirarFoto no CameraUtils.kt) e o mesmo
 * <ID_Gerado> vai na coluna do CSV. Ex.: "CCN-51_Parcela1_Planta3_1761693000123.jpg".
 *
 * Se a mesma planta foi fotografada mais de uma vez (REFAZER FOTO), alinhamos pelo FIM:
 * a ultima linha da planta fica com a ultima foto, a penultima com a penultima, e assim por diante.
 */
@Component
public class FotoMatcher {

    private static final Pattern NOME_FOTO = Pattern.compile("^(.+)_(\\d{10,})(?:\\s*\\(\\d+\\))?$");

    public record Resultado(List<ArquivoFoto> fotoPorLinha, int fotosSemPar) {}

    public Resultado casar(List<LinhaColetaCsvDto> linhas, List<ArquivoFoto> fotos) {
        List<ArquivoFoto> porLinha = new ArrayList<>(Collections.nCopies(linhas.size(), (ArquivoFoto) null));
        if (fotos == null || fotos.isEmpty()) {
            return new Resultado(porLinha, 0);
        }

        Map<String, List<ArquivoFoto>> fotosPorPlanta = new HashMap<>();
        for (ArquivoFoto foto : fotos) {
            Matcher m = NOME_FOTO.matcher(semExtensao(foto.nomeOriginal()));
            if (m.matches()) {
                fotosPorPlanta.computeIfAbsent(m.group(1).toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(foto);
            }
        }
        // Ordem cronologica = ordem do carimbo de tempo no nome do arquivo.
        fotosPorPlanta.values().forEach(lista -> lista.sort(Comparator.comparingLong(f -> carimbo(f.nomeOriginal()))));

        Map<String, List<Integer>> linhasPorPlanta = new LinkedHashMap<>();
        for (int i = 0; i < linhas.size(); i++) {
            linhasPorPlanta.computeIfAbsent(linhas.get(i).plantId().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(i);
        }

        int casadas = 0;
        for (Map.Entry<String, List<Integer>> entrada : linhasPorPlanta.entrySet()) {
            List<ArquivoFoto> candidatas = fotosPorPlanta.get(entrada.getKey());
            if (candidatas == null) {
                continue;
            }
            List<Integer> indices = entrada.getValue();
            int l = indices.size() - 1;
            int f = candidatas.size() - 1;
            while (l >= 0 && f >= 0) {
                porLinha.set(indices.get(l), candidatas.get(f));
                casadas++;
                l--;
                f--;
            }
        }
        return new Resultado(porLinha, fotos.size() - casadas);
    }

    private String semExtensao(String nome) {
        if (nome == null) {
            return "";
        }
        String soNome = nome.replace('\\', '/');
        soNome = soNome.substring(soNome.lastIndexOf('/') + 1);
        int ponto = soNome.lastIndexOf('.');
        return (ponto > 0 ? soNome.substring(0, ponto) : soNome).trim();
    }

    private long carimbo(String nome) {
        Matcher m = NOME_FOTO.matcher(semExtensao(nome));
        return m.matches() ? Long.parseLong(m.group(2)) : 0L;
    }
}
