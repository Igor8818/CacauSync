package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.ArquivoFoto;
import br.gov.ceplac.cepec.cacausync.dto.DeteccaoDto;
import br.gov.ceplac.cepec.cacausync.dto.LeituraColetaResponse;
import br.gov.ceplac.cepec.cacausync.dto.LeituraSincronizadaDto;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoImportacaoCsvResponse;
import br.gov.ceplac.cepec.cacausync.dto.SyncJsonRequest;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ColetaSyncService {

    private final CsvColetaParser csvColetaParser;
    private final EnsaioRepository ensaioRepository;
    private final CloneRepository cloneRepository;
    private final ParcelaRepository parcelaRepository;
    private final PlantaRepository plantaRepository;
    private final LeituraColetaRepository leituraColetaRepository;
    private final FotoMatcher fotoMatcher;
    private final FotoStorageService fotoStorageService;
    private final LeituraMapper leituraMapper;

    public ColetaSyncService(
            CsvColetaParser csvColetaParser,
            EnsaioRepository ensaioRepository,
            CloneRepository cloneRepository,
            ParcelaRepository parcelaRepository,
            PlantaRepository plantaRepository,
            LeituraColetaRepository leituraColetaRepository,
            FotoMatcher fotoMatcher,
            FotoStorageService fotoStorageService,
            LeituraMapper leituraMapper
    ) {
        this.csvColetaParser = csvColetaParser;
        this.ensaioRepository = ensaioRepository;
        this.cloneRepository = cloneRepository;
        this.parcelaRepository = parcelaRepository;
        this.plantaRepository = plantaRepository;
        this.leituraColetaRepository = leituraColetaRepository;
        this.fotoMatcher = fotoMatcher;
        this.fotoStorageService = fotoStorageService;
        this.leituraMapper = leituraMapper;
    }

    /** Importacao so de CSV (sem fotos) â€” mantida por compatibilidade. */
    @Transactional
    public ResultadoImportacaoCsvResponse importarCsv(
            String codigoEnsaio, InputStream csvInputStream, String nomeArquivo, String pesquisadorEmail
    ) {
        return importarCsv(codigoEnsaio, csvInputStream, nomeArquivo, pesquisadorEmail, List.of());
    }

    /** CSV do app + fotos tiradas em campo (casadas com as linhas pelo nome do arquivo). */
    @Transactional
    public ResultadoImportacaoCsvResponse importarCsv(
            String codigoEnsaio,
            InputStream csvInputStream,
            String nomeArquivo,
            String pesquisadorEmail,
            List<ArquivoFoto> fotos
    ) {
        List<LinhaColetaCsvDto> linhas = csvColetaParser.parse(csvInputStream);
        return processarLinhas(codigoEnsaio, linhas, nomeArquivo, pesquisadorEmail, fotos);
    }

    /** Sincronizacao em JSON (contrato para o futuro botao do app): ja leva as caixas do YOLO. */
    @Transactional
    public ResultadoImportacaoCsvResponse sincronizarJson(SyncJsonRequest request, String pesquisadorEmail) {
        List<LinhaColetaCsvDto> linhas = request.leituras().stream()
                .map(item -> {
                    boolean col = CsvColetaParser.isPlantaColetavel(item.resultado());
                    int qtd = col ? CsvColetaParser.extrairQuantidade(item.resultado()) : 0;
                    List<DeteccaoDto> dets = item.deteccoes() != null ? item.deteccoes() : List.of();
                    if (item.sadios() != null || item.vassoura() != null || item.podridao() != null || item.naoAvaliavel() != null) {
                        return new LinhaColetaCsvDto(
                                item.campanha().trim(),
                                item.plantId().trim(),
                                item.cloneCodigo() != null && !item.cloneCodigo().isBlank() ? item.cloneCodigo().trim() : extrairClone(item.plantId()),
                                item.parcela() != null && !item.parcela().isBlank() ? item.parcela().trim() : "1",
                                item.planta() != null ? item.planta() : 1,
                                item.resultado().trim(),
                                qtd,
                                col && item.sadios() != null ? item.sadios() : 0,
                                col && item.vassoura() != null ? item.vassoura() : 0,
                                col && item.podridao() != null ? item.podridao() : 0,
                                col && item.naoAvaliavel() != null ? item.naoAvaliavel() : 0,
                                col,
                                item.data().trim(),
                                item.lat() != null ? item.lat() : 0.0,
                                item.lng() != null ? item.lng() : 0.0,
                                dets
                        );
                    }
                    return new LinhaColetaCsvDto(
                            item.campanha().trim(),
                            item.plantId().trim(),
                            item.cloneCodigo() != null && !item.cloneCodigo().isBlank() ? item.cloneCodigo().trim() : extrairClone(item.plantId()),
                            item.parcela() != null && !item.parcela().isBlank() ? item.parcela().trim() : "1",
                            item.planta() != null ? item.planta() : 1,
                            item.resultado().trim(),
                            qtd,
                            col,
                            item.data().trim(),
                            item.lat() != null ? item.lat() : 0.0,
                            item.lng() != null ? item.lng() : 0.0,
                            dets
                    );
                })
                .toList();
        return processarLinhas(request.codigoEnsaio(), linhas, "sync-json", pesquisadorEmail, List.of());
    }

    private ResultadoImportacaoCsvResponse processarLinhas(
            String codigoEnsaio,
            List<LinhaColetaCsvDto> linhas,
            String nomeArquivo,
            String pesquisadorEmail,
            List<ArquivoFoto> fotos
    ) {
        String codigoFinal = (codigoEnsaio != null && !codigoEnsaio.isBlank())
                ? codigoEnsaio.trim().toUpperCase()
                : extrairEnsaioDaCampanha(linhas.get(0).campanha());

        Ensaio ensaio = ensaioRepository.findByCodigoIgnoreCase(codigoFinal)
                .orElseGet(() -> ensaioRepository.save(new Ensaio(
                        codigoFinal,
                        "Ensaio GenÃ©tico " + codigoFinal + " (Importado via CacauApp)",
                        "CEPEC/CEPLAC - IlhÃ©us/Itabuna, BA"
                )));

        FotoMatcher.Resultado casamento = fotoMatcher.casar(linhas, fotos);

        int inseridas = 0;
        int duplicadas = 0;
        int naoColetaveis = 0;
        int alertasGps = 0;
        int somaFrutos = 0;
        int fotosAnexadas = 0;
        List<String> avisos = new ArrayList<>();
        List<LeituraSincronizadaDto> leiturasSincronizadas = new ArrayList<>();

        for (int i = 0; i < linhas.size(); i++) {
            LinhaColetaCsvDto item = linhas.get(i);
            ArquivoFoto foto = casamento.fotoPorLinha().get(i);
            String chaveIdempotencia = gerarChaveIdempotencia(codigoFinal, item);

            Optional<LeituraColeta> existente = leituraColetaRepository.findByChaveIdempotencia(chaveIdempotencia);
            if (existente.isPresent()) {
                duplicadas++;
                // Caso comum: o CSV foi enviado antes e agora chegaram as fotos, ou o operador reclassificou no celular.
                LeituraColeta ja = existente.get();
                leiturasSincronizadas.add(new LeituraSincronizadaDto(item.plantId(), ja.getId()));
                boolean alterou = false;
                if (ja.getStatusValidacao() == StatusValidacao.PENDENTE
                        && item.deteccoes() != null && !item.deteccoes().isEmpty()
                        && (!java.util.Objects.equals(ja.getQtdSadios(), item.qtdSadios())
                            || !java.util.Objects.equals(ja.getQtdVassoura(), item.qtdVassoura())
                            || !java.util.Objects.equals(ja.getQtdPodridao(), item.qtdPodridao())
                            || !java.util.Objects.equals(ja.getQtdNaoAvaliavel(), item.qtdNaoAvaliavel()))) {
                    ja.setQtdFrutos(item.qtdFrutos());
                    ja.setQtdSadios(item.qtdSadios());
                    ja.setQtdVassoura(item.qtdVassoura());
                    ja.setQtdPodridao(item.qtdPodridao());
                    ja.setQtdNaoAvaliavel(item.qtdNaoAvaliavel());
                    ja.setDeteccoesJson(leituraMapper.serializarDeteccoes(item.deteccoes()));
                    alterou = true;
                }
                if (foto != null && !ja.temFoto() && anexarFotoSegura(ja, foto, avisos)) {
                    fotosAnexadas++;
                    alterou = true;
                }
                if (alterou) {
                    leituraColetaRepository.save(ja);
                }
                continue;
            }

            Planta planta = resolverOuCriarPlanta(ensaio, item);

            if (!item.coletavel()) {
                planta.setSituacao(SituacaoPlanta.NAO_COLETAVEL);
                plantaRepository.save(planta);
                naoColetaveis++;
                avisos.add("Planta " + planta.getPlantCode() + " marcada como NAO_COLETAVEL (DiagnÃ³stico: " + item.resultadoBruto() + ")");
            } else if (planta.getSituacao() == SituacaoPlanta.NAO_COLETAVEL && item.qtdFrutos() > 0) {
                planta.setSituacao(SituacaoPlanta.NORMAL);
                plantaRepository.save(planta);
            }

            boolean gpsZerado = isCoordenadaGpsZerada(item.latitude(), item.longitude());
            if (gpsZerado) {
                alertasGps++;
            }

            LeituraColeta leitura = new LeituraColeta();
            leitura.setPlanta(planta);
            leitura.setPesquisadorEmail(pesquisadorEmail);
            leitura.setCampanha(item.campanha());
            leitura.setQtdFrutos(item.qtdFrutos());
            leitura.setQtdSadios(item.qtdSadios());
            leitura.setQtdVassoura(item.qtdVassoura());
            leitura.setQtdPodridao(item.qtdPodridao());
            leitura.setQtdNaoAvaliavel(item.qtdNaoAvaliavel());
            leitura.setColetavel(item.coletavel());
            leitura.setDiagnosticoBruto(item.resultadoBruto());
            leitura.setDataColetaStr(item.dataColeta());
            leitura.setLatitude(item.latitude());
            leitura.setLongitude(item.longitude());
            leitura.setAlertaGps(gpsZerado);
            leitura.setChaveIdempotencia(chaveIdempotencia);
            leitura.setDeteccoesJson(leituraMapper.serializarDeteccoes(item.deteccoes()));

            if (foto != null && anexarFotoSegura(leitura, foto, avisos)) {
                fotosAnexadas++;
            }

            LeituraColeta salva = leituraColetaRepository.save(leitura);
            Long idSalvo = (salva != null) ? salva.getId() : leitura.getId();
            leiturasSincronizadas.add(new LeituraSincronizadaDto(item.plantId(), idSalvo));
            inseridas++;
            somaFrutos += item.qtdFrutos();
        }

        int fotosRecebidas = fotos == null ? 0 : fotos.size();
        int fotosIgnoradas = fotosRecebidas - fotosAnexadas;
        if (fotosIgnoradas > 0) {
            avisos.add(fotosIgnoradas + " foto(s) nÃ£o anexada(s): o nome nÃ£o corresponde a nenhuma linha do CSV, "
                    + "a leitura jÃ¡ tinha foto, ou o arquivo era invÃ¡lido.");
        }

        return new ResultadoImportacaoCsvResponse(
                ensaio.getCodigo(),
                nomeArquivo != null ? nomeArquivo : "planilha_campo.csv",
                linhas.size(),
                inseridas,
                duplicadas,
                naoColetaveis,
                alertasGps,
                somaFrutos,
                fotosAnexadas,
                fotosIgnoradas,
                LocalDateTime.now(),
                avisos,
                leiturasSincronizadas
        );
    }

    /** Uma foto ruim nao pode derrubar a importacao inteira: registramos o aviso e seguimos. */
    private boolean anexarFotoSegura(LeituraColeta leitura, ArquivoFoto foto, List<String> avisos) {
        try {
            FotoStorageService.FotoSalva salva = fotoStorageService.salvar(foto.conteudo());
            leitura.setFotoChave(salva.chave());
            leitura.setFotoContentType(salva.contentType());
            return true;
        } catch (RegraNegocioException e) {
            avisos.add("Foto '" + foto.nomeOriginal() + "' rejeitada: " + e.getMessage());
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Page<LeituraColetaResponse> listarLeiturasPorEnsaio(String codigoEnsaio, StatusValidacao status, Pageable pageable) {
        Page<LeituraColeta> pagina = (status == null)
                ? leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCase(codigoEnsaio, pageable)
                : leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseAndStatusValidacao(codigoEnsaio, status, pageable);
        return pagina.map(leituraMapper::paraResponse);
    }

    private Planta resolverOuCriarPlanta(Ensaio ensaio, LinhaColetaCsvDto item) {
        String cloneInformado = (item.cloneCodigo() != null && !item.cloneCodigo().isBlank())
                ? item.cloneCodigo().trim().toUpperCase()
                : extrairClone(item.plantId()).toUpperCase();

        return plantaRepository.findByPlantCodeIgnoreCase(item.plantId())
                .orElseGet(() -> {
                    Clone clone = cloneRepository.findByCodigoIgnoreCase(cloneInformado)
                            .orElseGet(() -> cloneRepository.save(new Clone(
                                    cloneInformado,
                                    "Clone registrado via sincronizaÃ§Ã£o de campo",
                                    "Programa GenÃ©tico CEPEC"
                            )));

                    String numParcela = (item.numeroParcela() == null || item.numeroParcela().isBlank()) ? "1" : item.numeroParcela().trim();

                    // Garante soberania do clone: uma parcela existente de OUTRO clone nunca pode sequestrar a leitura!
                    Optional<Parcela> parcelaExistente = parcelaRepository.findByEnsaioAndNumeroParcela(ensaio, numParcela);
                    Parcela parcela;

                    if (parcelaExistente.isPresent() && parcelaExistente.get().getClone().getCodigo().equalsIgnoreCase(clone.getCodigo())) {
                        parcela = parcelaExistente.get();
                    } else if (parcelaExistente.isPresent() && !parcelaExistente.get().getClone().getCodigo().equalsIgnoreCase(clone.getCodigo())) {
                        // A parcela informada pertence a outro clone no ensaio (ex: parcela 1 pertence a SCP-16005, mas o usuÃ¡rio coletou CCN-51).
                        // Busca se jÃ¡ existe uma parcela real desse clone no ensaio (ex: CCN-51 -> Parcela 20).
                        Optional<Parcela> parcelaDoClone = parcelaRepository.findByEnsaioCodigoIgnoreCase(ensaio.getCodigo()).stream()
                                .filter(p -> p.getClone().getCodigo().equalsIgnoreCase(clone.getCodigo()))
                                .findFirst();

                        if (parcelaDoClone.isPresent()) {
                            parcela = parcelaDoClone.get();
                        } else {
                            // Cria uma parcela especÃ­fica com sufixo do clone para nÃ£o violar uk_ensaio_parcela
                            String numParcelaDedicada = numParcela + "-" + clone.getCodigo();
                            parcela = parcelaRepository.findByEnsaioAndNumeroParcela(ensaio, numParcelaDedicada)
                                    .orElseGet(() -> parcelaRepository.save(new Parcela(ensaio, clone, 1, numParcelaDedicada)));
                        }
                    } else {
                        parcela = parcelaRepository.save(new Parcela(ensaio, clone, 1, numParcela));
                    }

                    return plantaRepository.findByParcelaAndNumeroPlanta(parcela, item.numeroPlanta())
                            .orElseGet(() -> {
                                SituacaoPlanta sit = item.coletavel() ? SituacaoPlanta.NORMAL : SituacaoPlanta.NAO_COLETAVEL;
                                int proximaOrdem = plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc(ensaio.getCodigo()).size() + 1;
                                Planta nova = new Planta(parcela, item.plantId(), item.numeroPlanta(), proximaOrdem, sit);
                                return plantaRepository.save(nova);
                            });
                });
    }

    public String gerarChaveIdempotencia(String codigoEnsaio, LinhaColetaCsvDto item) {
        return String.format("%s|%s|%s|%s",
                codigoEnsaio.toUpperCase(),
                item.plantId().toUpperCase(),
                item.campanha().toUpperCase(),
                item.dataColeta()
        );
    }

    public boolean isCoordenadaGpsZerada(double lat, double lng) {
        return Math.abs(lat) < 0.0001 && Math.abs(lng) < 0.0001;
    }

    private String extrairClone(String plantId) {
        String[] partes = plantId.trim().split("_");
        return partes.length > 0 && !partes[0].isBlank() ? partes[0] : "CLONE-DESCONHECIDO";
    }

    private String extrairEnsaioDaCampanha(String campanha) {
        if (campanha == null || campanha.isBlank()) {
            return "TC-1801";
        }
        return campanha.trim().toUpperCase();
    }
}

