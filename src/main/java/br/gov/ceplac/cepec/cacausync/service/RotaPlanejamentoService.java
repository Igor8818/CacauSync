package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.ItemRotaDto;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoRotaResponse;
import br.gov.ceplac.cepec.cacausync.exception.RecursoNaoEncontradoException;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.repository.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class RotaPlanejamentoService {

    private final EnsaioRepository ensaioRepository;
    private final CloneRepository cloneRepository;
    private final ParcelaRepository parcelaRepository;
    private final PlantaRepository plantaRepository;
    private final LeituraColetaRepository leituraColetaRepository;
    private final UsbAdbSyncService usbAdbSyncService;

    public RotaPlanejamentoService(
            EnsaioRepository ensaioRepository,
            CloneRepository cloneRepository,
            ParcelaRepository parcelaRepository,
            PlantaRepository plantaRepository,
            LeituraColetaRepository leituraColetaRepository,
            UsbAdbSyncService usbAdbSyncService
    ) {
        this.ensaioRepository = ensaioRepository;
        this.cloneRepository = cloneRepository;
        this.parcelaRepository = parcelaRepository;
        this.plantaRepository = plantaRepository;
        this.leituraColetaRepository = leituraColetaRepository;
        this.usbAdbSyncService = usbAdbSyncService;
    }

    @Transactional(readOnly = true)
    public List<ItemRotaDto> listarRotaDoEnsaio(String codigoEnsaio) {
        Ensaio ensaio = ensaioRepository.findByCodigoIgnoreCase(codigoEnsaio)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ensaio não encontrado: " + codigoEnsaio));

        List<Planta> plantas = plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc(ensaio.getCodigo());
        List<LeituraColeta> leituras = leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(ensaio.getCodigo());

        Map<Long, LeituraColeta> leituraMaisRecentePorPlanta = new HashMap<>();
        for (LeituraColeta l : leituras) {
            if (l.getPlanta() != null && l.getPlanta().getId() != null) {
                leituraMaisRecentePorPlanta.putIfAbsent(l.getPlanta().getId(), l);
            }
        }

        List<ItemRotaDto> resultado = new ArrayList<>(plantas.size());
        for (Planta p : plantas) {
            LeituraColeta l = leituraMaisRecentePorPlanta.get(p.getId());
            boolean coletado = l != null && l.getStatusValidacao() != StatusValidacao.REJEITADA;
            String qtdFrutos = "0";
            if (l != null) {
                qtdFrutos = Boolean.TRUE.equals(l.getColetavel())
                        ? String.valueOf(l.qtdEfetiva())
                        : "Não Coletável";
            }
            Long leituraId = l != null ? l.getId() : null;
            boolean temFoto = l != null && l.temFoto();
            String statusValidacao = l != null ? l.getStatusValidacao().name() : "AGUARDANDO_COLETA";

            resultado.add(new ItemRotaDto(
                    p.getOrdemRota() != null ? p.getOrdemRota() : 1,
                    p.getPlantCode(),
                    p.getParcela().getClone().getCodigo(),
                    p.getParcela().getNumeroParcela(),
                    p.getNumeroPlanta(),
                    p.getParcela().getBloco() != null ? p.getParcela().getBloco() : 1,
                    p.getSituacao().name(),
                    coletado,
                    qtdFrutos,
                    leituraId,
                    temFoto,
                    statusValidacao
            ));
        }
        return resultado;
    }

    @Transactional
    public ResultadoRotaResponse importarPlanilhaRota(
            String codigoEnsaio,
            InputStream inputStream,
            String nomeArquivo,
            boolean enviarParaUsb
    ) {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RegraNegocioException("Erro ao ler o arquivo da planilha de rota: " + e.getMessage());
        }

        if (bytes.length == 0) {
            throw new RegraNegocioException("O arquivo de planilha enviado está vazio.");
        }

        boolean isXlsx = bytes.length > 4 && (bytes[0] & 0xFF) == 0x50 && (bytes[1] & 0xFF) == 0x4B;
        List<List<String>> tabela = isXlsx ? extrairLinhasXlsx(bytes) : extrairLinhasCsv(bytes);

        if (tabela.size() < 2) {
            throw new RegraNegocioException("A planilha precisa conter cabeçalho e ao menos 1 planta na rota.");
        }

        List<String> cabecalho = tabela.get(0);
        int idxClone = localizarColuna(cabecalho, List.of("clone", "v_id", "variedade", "genotipo"));
        int idxParcela = localizarColuna(cabecalho, List.of("parcela", "rep", "repeticao", "talhao"));
        int idxPlanta = localizarColuna(cabecalho, List.of("planta", "p_id", "num_planta", "planta_id"));
        int idxOrdem = localizarColuna(cabecalho, List.of("ordem", "ordem_rota", "seq", "sequencia"));
        int idxBloco = localizarColuna(cabecalho, List.of("bloco", "block"));
        int idxPlantId = localizarColuna(cabecalho, List.of("plant_id", "id_gerado", "codigo_planta"));
        int idxSitC = localizarColuna(cabecalho, List.of("sitpla2023c", "sitpla", "situacao"));
        int idxSitL = localizarColuna(cabecalho, List.of("sitpla2023l", "historico"));

        if (idxClone == -1 || idxParcela == -1 || idxPlanta == -1) {
            throw new RegraNegocioException(
                    "Não foi possível localizar as colunas obrigatórias (Clone, Parcela, Planta) no cabeçalho: "
                    + String.join(", ", cabecalho)
            );
        }

        String codigoFinal = (codigoEnsaio != null && !codigoEnsaio.isBlank())
                ? codigoEnsaio.trim().toUpperCase(Locale.ROOT)
                : "TC-1801";

        Ensaio ensaio = ensaioRepository.findByCodigoIgnoreCase(codigoFinal)
                .orElseGet(() -> ensaioRepository.save(new Ensaio(
                        codigoFinal,
                        "Ensaio Genético " + codigoFinal,
                        "CEPEC/CEPLAC - Ilhéus/Itabuna, BA"
                )));

        // Remove plantas antigas que ainda não possuem leituras para que a rota reflita fielmente a nova planilha
        List<Planta> plantasExistentes = plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc(ensaio.getCodigo());
        List<LeituraColeta> leiturasExistentes = leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(ensaio.getCodigo());
        Set<Long> plantasComLeitura = new HashSet<>();
        for (LeituraColeta l : leiturasExistentes) {
            if (l.getPlanta() != null && l.getPlanta().getId() != null) {
                plantasComLeitura.add(l.getPlanta().getId());
            }
        }

        Map<String, Clone> cacheClones = new HashMap<>();
        for (Clone c : cloneRepository.findAll()) {
            cacheClones.put(c.getCodigo().toUpperCase(Locale.ROOT), c);
        }

        Map<String, Parcela> cacheParcelas = new HashMap<>();
        for (Parcela par : parcelaRepository.findByEnsaioCodigoIgnoreCase(ensaio.getCodigo())) {
            cacheParcelas.put(par.getNumeroParcela().toUpperCase(Locale.ROOT), par);
        }

        Map<String, Planta> porParcelaEPlanta = new HashMap<>();
        Map<String, Planta> porPlantCode = new HashMap<>();
        for (Planta p : plantasExistentes) {
            String chave = p.getParcela().getNumeroParcela().toUpperCase(Locale.ROOT) + "#" + p.getNumeroPlanta();
            porParcelaEPlanta.put(chave, p);
            porPlantCode.put(p.getPlantCode().toUpperCase(Locale.ROOT), p);
        }

        Set<Long> plantasTocadas = new HashSet<>();
        int contadorSequencial = 0;

        for (int i = 1; i < tabela.size(); i++) {
            List<String> cols = tabela.get(i);
            String cloneCod = valorColuna(cols, idxClone).trim();
            String parcelaNum = valorColuna(cols, idxParcela).trim();
            String plantaStr = valorColuna(cols, idxPlanta).trim();
            if (cloneCod.isEmpty() || parcelaNum.isEmpty() || plantaStr.isEmpty()) {
                continue;
            }

            contadorSequencial++;
            int numPlanta = extrairInteiroPositivo(plantaStr, 1);
            int ordemRota = idxOrdem != -1 ? extrairInteiroPositivo(valorColuna(cols, idxOrdem), contadorSequencial) : contadorSequencial;
            int bloco = idxBloco != -1 ? extrairInteiroPositivo(valorColuna(cols, idxBloco), 1) : 1;

            String plantCodePlanilha = idxPlantId != -1 ? valorColuna(cols, idxPlantId).trim() : "";
            String plantCodePadrao = cloneCod + "_P" + parcelaNum + "_Pl" + numPlanta;
            String plantCodeEfetivo = !plantCodePlanilha.isEmpty() ? plantCodePlanilha : plantCodePadrao;

            String sitC = idxSitC != -1 ? valorColuna(cols, idxSitC).trim() : "N11_P0_E0_M0_V0";
            String sitL = idxSitL != -1 ? valorColuna(cols, idxSitL).trim() : "nnnnnnnnnnn";
            SituacaoPlanta situacao = isPlantaMortaNaPlanilha(sitC, sitL)
                    ? SituacaoPlanta.NAO_COLETAVEL
                    : SituacaoPlanta.NORMAL;

            Clone clone = cacheClones.computeIfAbsent(cloneCod.toUpperCase(Locale.ROOT), k ->
                    cloneRepository.findByCodigoIgnoreCase(cloneCod)
                            .orElseGet(() -> cloneRepository.save(new Clone(
                                    cloneCod.toUpperCase(Locale.ROOT),
                                    "Clone " + cloneCod.toUpperCase(Locale.ROOT),
                                    "Programa Genético CEPEC"
                            )))
            );

            Parcela parcela = cacheParcelas.get(parcelaNum.toUpperCase(Locale.ROOT));
            if (parcela == null) {
                parcela = new Parcela(ensaio, clone, bloco, parcelaNum);
                parcela = parcelaRepository.save(parcela);
                cacheParcelas.put(parcelaNum.toUpperCase(Locale.ROOT), parcela);
            } else {
                parcela.setClone(clone);
                parcela.setBloco(bloco);
                parcela = parcelaRepository.save(parcela);
            }

            String chaveParPla = parcelaNum.toUpperCase(Locale.ROOT) + "#" + numPlanta;
            Planta planta = porParcelaEPlanta.get(chaveParPla);
            if (planta == null) {
                planta = porPlantCode.get(plantCodeEfetivo.toUpperCase(Locale.ROOT));
            }

            if (planta == null) {
                // Evita conflito de uk_plant_code caso código curto como "101_P1" se repita ou exista em outro ensaio
                String codigoUnico = garantirPlantCodeUnico(plantCodeEfetivo, cloneCod, parcelaNum, numPlanta, porPlantCode);
                planta = new Planta(parcela, codigoUnico, numPlanta, ordemRota, situacao);
                planta.setSitPlaConsolidado(sitC.isEmpty() ? "N11_P0_E0_M0_V0" : sitC);
                planta.setSitPlaHistorico(sitL.isEmpty() ? "nnnnnnnnnnn" : sitL);
                planta = plantaRepository.save(planta);
                porParcelaEPlanta.put(chaveParPla, planta);
                porPlantCode.put(codigoUnico.toUpperCase(Locale.ROOT), planta);
            } else {
                planta.setParcela(parcela);
                planta.setNumeroPlanta(numPlanta);
                planta.setOrdemRota(ordemRota);
                if (!plantasComLeitura.contains(planta.getId())) {
                    planta.setSituacao(situacao);
                }
                if (!sitC.isEmpty()) planta.setSitPlaConsolidado(sitC);
                if (!sitL.isEmpty()) planta.setSitPlaHistorico(sitL);
                planta = plantaRepository.save(planta);
            }
            if (planta.getId() != null) {
                plantasTocadas.add(planta.getId());
            }
        }

        // Remove plantas órfãs antigas (sem leitura) que não fazem parte da planilha importada
        for (Planta antiga : plantasExistentes) {
            if (antiga.getId() != null
                    && !plantasTocadas.contains(antiga.getId())
                    && !plantasComLeitura.contains(antiga.getId())) {
                plantaRepository.delete(antiga);
            }
        }
        plantaRepository.flush();

        List<ItemRotaDto> itensRota = listarRotaDoEnsaio(ensaio.getCodigo());
        int coletadas = (int) itensRota.stream().filter(ItemRotaDto::coletado).count();

        boolean enviadoUsb = false;
        String serialUsb = null;
        String msg;

        if (enviarParaUsb) {
            try {
                serialUsb = usbAdbSyncService.enviarRotaParaCelularViaUsb(ensaio.getCodigo(), itensRota);
                enviadoUsb = true;
                msg = "Planilha importada (" + itensRota.size() + " plantas) e enviada diretamente para o celular via USB (" + serialUsb + ")!";
            } catch (Exception e) {
                msg = "Planilha importada no CacauSync (" + itensRota.size() + " plantas). Aviso USB: " + e.getMessage();
            }
        } else {
            msg = "Planilha de rota importada com sucesso (" + itensRota.size() + " plantas na sequência da planilha).";
        }

        return new ResultadoRotaResponse(
                ensaio.getCodigo(),
                nomeArquivo != null ? nomeArquivo : "planilha_rota",
                itensRota.size(),
                coletadas,
                enviadoUsb,
                serialUsb,
                msg,
                LocalDateTime.now(),
                itensRota
        );
    }

    @Transactional
    public ResultadoRotaResponse carregarRotaCompletaTc1801(String codigoEnsaio, boolean enviarParaUsb) {
        try {
            ClassPathResource resource = new ClassPathResource("rotas/tc1801_rota_completa.csv");
            try (InputStream is = resource.getInputStream()) {
                return importarPlanilhaRota(codigoEnsaio, is, "TC1801_PatriaMoni_940_plantas.csv", enviarParaUsb);
            }
        } catch (IOException e) {
            throw new RegraNegocioException("Não foi possível carregar a rota padrão do TC-1801: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResultadoRotaResponse enviarRotaAtualParaUsb(String codigoEnsaio) {
        List<ItemRotaDto> itensRota = listarRotaDoEnsaio(codigoEnsaio);
        if (itensRota.isEmpty()) {
            throw new RegraNegocioException("O ensaio " + codigoEnsaio + " não possui plantas cadastradas na rota.");
        }
        String serial = usbAdbSyncService.enviarRotaParaCelularViaUsb(codigoEnsaio, itensRota);
        int coletadas = (int) itensRota.stream().filter(ItemRotaDto::coletado).count();

        return new ResultadoRotaResponse(
                codigoEnsaio.toUpperCase(Locale.ROOT),
                "Rota ativa do ensaio " + codigoEnsaio.toUpperCase(Locale.ROOT),
                itensRota.size(),
                coletadas,
                true,
                serial,
                "Rota com " + itensRota.size() + " plantas enviada com sucesso para o celular (" + serial + ")! Abra a aba 'Modo Rota' no CacauApp.",
                LocalDateTime.now(),
                itensRota
        );
    }

    private String garantirPlantCodeUnico(
            String candidato, String clone, String parcela, int numPlanta, Map<String, Planta> porPlantCode
    ) {
        if (!porPlantCode.containsKey(candidato.toUpperCase(Locale.ROOT))
                && plantaRepository.findByPlantCodeIgnoreCase(candidato).isEmpty()) {
            return candidato;
        }
        String alternativo = clone + "_P" + parcela + "_Pl" + numPlanta;
        if (!porPlantCode.containsKey(alternativo.toUpperCase(Locale.ROOT))
                && plantaRepository.findByPlantCodeIgnoreCase(alternativo).isEmpty()) {
            return alternativo;
        }
        return alternativo + "_" + System.nanoTime();
    }

    private static boolean isPlantaMortaNaPlanilha(String sitC, String sitL) {
        String c = sitC != null ? sitC.trim().toUpperCase(Locale.ROOT) : "";
        String l = sitL != null ? sitL.trim().toUpperCase(Locale.ROOT) : "";
        if (c.equals("M") || c.equals("MORTA") || c.equals("AUSENTE") || c.equals("NAO_COLETAVEL")) {
            return true;
        }
        if (c.startsWith("N0_P0_E0_M") && !c.endsWith("_M0_V0")) {
            return true;
        }
        return l.equals("MMMMMMMMMMM");
    }

    private static int localizarColuna(List<String> cabecalho, List<String> aliases) {
        for (String alias : aliases) {
            for (int i = 0; i < cabecalho.size(); i++) {
                String col = limparNomeColuna(cabecalho.get(i));
                if (col.equals(alias)) {
                    return i;
                }
            }
        }
        for (String alias : aliases) {
            for (int i = 0; i < cabecalho.size(); i++) {
                String col = limparNomeColuna(cabecalho.get(i));
                if (col.contains(alias)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String limparNomeColuna(String col) {
        if (col == null) return "";
        return col.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String valorColuna(List<String> cols, int idx) {
        if (idx < 0 || idx >= cols.size()) return "";
        String v = cols.get(idx);
        return v != null ? v : "";
    }

    private static int extrairInteiroPositivo(String s, int padrao) {
        if (s == null || s.isBlank()) return padrao;
        String limpo = s.trim();
        if (limpo.contains(".")) {
            limpo = limpo.substring(0, limpo.indexOf('.'));
        }
        String digitos = limpo.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) return padrao;
        try {
            int val = Integer.parseInt(digitos);
            return val > 0 ? val : padrao;
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private List<List<String>> extrairLinhasCsv(byte[] bytes) {
        String texto = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
        List<List<String>> linhas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(texto))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                linhas.add(fatiarLinhaCsv(line));
            }
        } catch (IOException ignored) {
        }
        return linhas;
    }

    private List<String> fatiarLinhaCsv(String linha) {
        char divisor = (linha.contains(";") && !linha.contains(",")) ? ';' : ',';
        List<String> cols = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean emAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                emAspas = !emAspas;
            } else if (c == divisor && !emAspas) {
                cols.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        cols.add(sb.toString().trim());
        return cols;
    }

    /**
     * Lê planilhas Excel (.xlsx) nativamente usando ZipInputStream + DOM XML da JDK padrão.
     */
    private List<List<String>> extrairLinhasXlsx(byte[] bytes) {
        byte[] sharedStringsBytes = null;
        byte[] sheet1Bytes = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("xl/sharedStrings.xml".equalsIgnoreCase(name)) {
                    sharedStringsBytes = zis.readAllBytes();
                } else if ("xl/worksheets/sheet1.xml".equalsIgnoreCase(name)) {
                    sheet1Bytes = zis.readAllBytes();
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RegraNegocioException("Erro ao descompactar planilha Excel (.xlsx): " + e.getMessage());
        }

        if (sheet1Bytes == null) {
            throw new RegraNegocioException("A planilha Excel (.xlsx) não contém a aba principal (sheet1.xml).");
        }

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();

            List<String> sharedStrings = new ArrayList<>();
            if (sharedStringsBytes != null) {
                Document ssDoc = db.parse(new ByteArrayInputStream(sharedStringsBytes));
                NodeList siNodes = ssDoc.getElementsByTagName("si");
                for (int i = 0; i < siNodes.getLength(); i++) {
                    Node si = siNodes.item(i);
                    sharedStrings.add(si.getTextContent() != null ? si.getTextContent().trim() : "");
                }
            }

            Document sheetDoc = db.parse(new ByteArrayInputStream(sheet1Bytes));
            NodeList rowNodes = sheetDoc.getElementsByTagName("row");
            List<List<String>> resultado = new ArrayList<>();

            for (int r = 0; r < rowNodes.getLength(); r++) {
                Element rowEl = (Element) rowNodes.item(r);
                NodeList cellNodes = rowEl.getElementsByTagName("c");
                List<String> linha = new ArrayList<>();

                for (int c = 0; c < cellNodes.getLength(); c++) {
                    Element cellEl = (Element) cellNodes.item(c);
                    String ref = cellEl.getAttribute("r");
                    int colIdx = converterColunaExcelParaIndice(ref, c);
                    while (linha.size() <= colIdx) {
                        linha.add("");
                    }

                    String tipo = cellEl.getAttribute("t");
                    String valor = "";
                    NodeList vList = cellEl.getElementsByTagName("v");
                    if (vList.getLength() > 0 && vList.item(0).getTextContent() != null) {
                        valor = vList.item(0).getTextContent().trim();
                        if ("s".equals(tipo)) {
                            try {
                                int ssIdx = Integer.parseInt(valor);
                                if (ssIdx >= 0 && ssIdx < sharedStrings.size()) {
                                    valor = sharedStrings.get(ssIdx);
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    } else {
                        NodeList isList = cellEl.getElementsByTagName("is");
                        if (isList.getLength() > 0 && isList.item(0).getTextContent() != null) {
                            valor = isList.item(0).getTextContent().trim();
                        }
                    }
                    linha.set(colIdx, valor);
                }

                boolean temConteudo = linha.stream().anyMatch(s -> s != null && !s.isBlank());
                if (temConteudo) {
                    resultado.add(linha);
                }
            }
            return resultado;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao processar células da planilha Excel (.xlsx): " + e.getMessage());
        }
    }

    private static int converterColunaExcelParaIndice(String cellRef, int fallback) {
        if (cellRef == null || cellRef.isEmpty()) return fallback;
        int idx = 0;
        boolean leuLetra = false;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = Character.toUpperCase(cellRef.charAt(i));
            if (ch >= 'A' && ch <= 'Z') {
                leuLetra = true;
                idx = idx * 26 + (ch - 'A' + 1);
            } else {
                break;
            }
        }
        return leuLetra ? (idx - 1) : fallback;
    }
}
