package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.dto.ArquivoFoto;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Casa fotos tiradas em campo com as linhas da planilha CSV.
 * Passo 1: Casamento exato pelo prefixo <ID_Gerado> antes do timestamp (_<milissegundos>.jpg).
 * Passo 2: Casamento por chave canônica CLONE|PARCELA|PLANTA (permite casar fotos do Modo Rota
 *          'SCP-16005_P1_Pl1_...' com planilhas 'SCP-16005_Parcela1_Planta1' ou 'SCP-16005_TC-1801_par1_pla1_c0226').
 */
@Component
public class FotoMatcher {

    private static final Pattern NOME_FOTO = Pattern.compile("^(.+)_(\\d{10,})(?:\\s*\\(\\d+\\))?$");
    private static final Pattern PARCELA_TOKEN = Pattern.compile("(?i)^(?:parcela|par|p)(\\d+)$");
    private static final Pattern PLANTA_TOKEN = Pattern.compile("(?i)^(?:planta|pla|pl)(\\d+)$");

    public record Resultado(List<ArquivoFoto> fotoPorLinha, int fotosSemPar) {}

    public Resultado casar(List<LinhaColetaCsvDto> linhas, List<ArquivoFoto> fotos) {
        List<ArquivoFoto> porLinha = new ArrayList<>(Collections.nCopies(linhas.size(), (ArquivoFoto) null));
        if (fotos == null || fotos.isEmpty()) {
            return new Resultado(porLinha, 0);
        }

        // Indexa todas as fotos válidas ordenadas por timestamp
        List<FotoIndexada> fotosIndexadas = new ArrayList<>();
        for (int idx = 0; idx < fotos.size(); idx++) {
            ArquivoFoto foto = fotos.get(idx);
            Matcher m = NOME_FOTO.matcher(semExtensao(foto.nomeOriginal()));
            if (m.matches()) {
                String prefixo = m.group(1).toUpperCase(Locale.ROOT).trim();
                long ts = Long.parseLong(m.group(2));
                fotosIndexadas.add(new FotoIndexada(idx, foto, prefixo, chaveCanonicaDePrefixo(prefixo), ts));
            }
        }
        fotosIndexadas.sort(Comparator.comparingLong(FotoIndexada::timestamp));

        boolean[] fotoUsada = new boolean[fotosIndexadas.size()];
        int casadas = 0;

        // Passo 1: Casamento exato por plantId
        Map<String, List<Integer>> linhasPorPlantId = new LinkedHashMap<>();
        for (int i = 0; i < linhas.size(); i++) {
            linhasPorPlantId.computeIfAbsent(linhas.get(i).plantId().toUpperCase(Locale.ROOT).trim(), k -> new ArrayList<>()).add(i);
        }

        for (Map.Entry<String, List<Integer>> entry : linhasPorPlantId.entrySet()) {
            String plantKey = entry.getKey();
            List<Integer> idxsFoto = new ArrayList<>();
            for (int f = 0; f < fotosIndexadas.size(); f++) {
                if (!fotoUsada[f] && fotosIndexadas.get(f).prefixo().equals(plantKey)) {
                    idxsFoto.add(f);
                }
            }
            List<Integer> idxsLinha = entry.getValue();
            int l = idxsLinha.size() - 1;
            int f = idxsFoto.size() - 1;
            while (l >= 0 && f >= 0) {
                int fotoIdx = idxsFoto.get(f);
                porLinha.set(idxsLinha.get(l), fotosIndexadas.get(fotoIdx).arquivo());
                fotoUsada[fotoIdx] = true;
                casadas++;
                l--;
                f--;
            }
        }

        // Passo 2: Fallback por chave canônica (CLONE|PARCELA|PLANTA) para linhas que ainda estão sem foto
        Map<String, List<Integer>> linhasPorCanonica = new LinkedHashMap<>();
        for (int i = 0; i < linhas.size(); i++) {
            if (porLinha.get(i) == null) {
                String canon = chaveCanonicaDaLinha(linhas.get(i));
                if (canon != null) {
                    linhasPorCanonica.computeIfAbsent(canon, k -> new ArrayList<>()).add(i);
                }
            }
        }

        for (Map.Entry<String, List<Integer>> entry : linhasPorCanonica.entrySet()) {
            String canon = entry.getKey();
            List<Integer> idxsFoto = new ArrayList<>();
            for (int f = 0; f < fotosIndexadas.size(); f++) {
                if (!fotoUsada[f] && canon.equals(fotosIndexadas.get(f).chaveCanonica())) {
                    idxsFoto.add(f);
                }
            }
            List<Integer> idxsLinha = entry.getValue();
            int l = idxsLinha.size() - 1;
            int f = idxsFoto.size() - 1;
            while (l >= 0 && f >= 0) {
                int fotoIdx = idxsFoto.get(f);
                porLinha.set(idxsLinha.get(l), fotosIndexadas.get(fotoIdx).arquivo());
                fotoUsada[fotoIdx] = true;
                casadas++;
                l--;
                f--;
            }
        }

        return new Resultado(porLinha, fotos.size() - casadas);
    }

    private String chaveCanonicaDaLinha(LinhaColetaCsvDto l) {
        if (l.cloneCodigo() == null || l.cloneCodigo().isBlank()) return null;
        String clone = l.cloneCodigo().trim().toUpperCase(Locale.ROOT);
        String parcela = (l.numeroParcela() == null || l.numeroParcela().isBlank()) ? "1" : l.numeroParcela().replaceAll("\\D+", "");
        if (parcela.isEmpty()) parcela = l.numeroParcela().trim().toUpperCase(Locale.ROOT);
        return clone + "|" + parcela + "|" + l.numeroPlanta();
    }

    private String chaveCanonicaDePrefixo(String prefixo) {
        String[] partes = prefixo.split("_");
        if (partes.length < 3) return null;
        String clone = partes[0].trim().toUpperCase(Locale.ROOT);
        String parcela = null;
        String planta = null;
        for (int i = 1; i < partes.length; i++) {
            Matcher mp = PARCELA_TOKEN.matcher(partes[i]);
            if (mp.matches() && parcela == null) {
                parcela = mp.group(1);
                continue;
            }
            Matcher mpl = PLANTA_TOKEN.matcher(partes[i]);
            if (mpl.matches() && planta == null) {
                planta = mpl.group(1);
            }
        }
        if (parcela != null && planta != null) {
            return clone + "|" + parcela + "|" + Integer.parseInt(planta);
        }
        return null;
    }

    private record FotoIndexada(int idxOriginal, ArquivoFoto arquivo, String prefixo, String chaveCanonica, long timestamp) {}

    private String semExtensao(String nome) {
        if (nome == null) {
            return "";
        }
        String soNome = nome.replace('\\', '/');
        soNome = soNome.substring(soNome.lastIndexOf('/') + 1);
        int ponto = soNome.lastIndexOf('.');
        return (ponto > 0 ? soNome.substring(0, ponto) : soNome).trim();
    }
}
