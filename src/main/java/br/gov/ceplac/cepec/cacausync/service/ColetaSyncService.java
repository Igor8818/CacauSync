package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.LeituraColetaResponse;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoImportacaoCsvResponse;
import br.gov.ceplac.cepec.cacausync.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ColetaSyncService {

    private final CsvColetaParser csvColetaParser;
    private final EnsaioRepository ensaioRepository;
    private final CloneRepository cloneRepository;
    private final ParcelaRepository parcelaRepository;
    private final PlantaRepository plantaRepository;
    private final LeituraColetaRepository leituraColetaRepository;

    public ColetaSyncService(
            CsvColetaParser csvColetaParser,
            EnsaioRepository ensaioRepository,
            CloneRepository cloneRepository,
            ParcelaRepository parcelaRepository,
            PlantaRepository plantaRepository,
            LeituraColetaRepository leituraColetaRepository
    ) {
        this.csvColetaParser = csvColetaParser;
        this.ensaioRepository = ensaioRepository;
        this.cloneRepository = cloneRepository;
        this.parcelaRepository = parcelaRepository;
        this.plantaRepository = plantaRepository;
        this.leituraColetaRepository = leituraColetaRepository;
    }

    @Transactional
    public ResultadoImportacaoCsvResponse importarCsv(
            String codigoEnsaio,
            InputStream csvInputStream,
            String nomeArquivo,
            String pesquisadorEmail
    ) {
        List<LinhaColetaCsvDto> linhas = csvColetaParser.parse(csvInputStream);

        String codigoFinal = (codigoEnsaio != null && !codigoEnsaio.isBlank())
                ? codigoEnsaio.trim().toUpperCase()
                : extrairEnsaioDaCampanha(linhas.get(0).campanha());

        Ensaio ensaio = ensaioRepository.findByCodigoIgnoreCase(codigoFinal)
                .orElseGet(() -> ensaioRepository.save(new Ensaio(
                        codigoFinal,
                        "Ensaio Genético " + codigoFinal + " (Importado via CacauApp)",
                        "CEPEC/CEPLAC - Ilhéus/Itabuna, BA"
                )));

        int inseridas = 0;
        int duplicadas = 0;
        int naoColetaveis = 0;
        int alertasGps = 0;
        int somaFrutos = 0;
        List<String> avisos = new ArrayList<>();

        for (LinhaColetaCsvDto item : linhas) {
            String chaveIdempotencia = gerarChaveIdempotencia(codigoFinal, item);

            if (leituraColetaRepository.existsByChaveIdempotencia(chaveIdempotencia)) {
                duplicadas++;
                continue;
            }

            Planta planta = resolverOuCriarPlanta(ensaio, item);

            if (!item.coletavel()) {
                planta.setSituacao(SituacaoPlanta.NAO_COLETAVEL);
                plantaRepository.save(planta);
                naoColetaveis++;
                avisos.add("Planta " + planta.getPlantCode() + " marcada como NAO_COLETAVEL (Diagnóstico: " + item.resultadoBruto() + ")");
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
            leitura.setColetavel(item.coletavel());
            leitura.setDiagnosticoBruto(item.resultadoBruto());
            leitura.setDataColetaStr(item.dataColeta());
            leitura.setLatitude(item.latitude());
            leitura.setLongitude(item.longitude());
            leitura.setAlertaGps(gpsZerado);
            leitura.setChaveIdempotencia(chaveIdempotencia);

            leituraColetaRepository.save(leitura);
            inseridas++;
            somaFrutos += item.qtdFrutos();
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
                LocalDateTime.now(),
                avisos
        );
    }

    @Transactional(readOnly = true)
    public Page<LeituraColetaResponse> listarLeiturasPorEnsaio(String codigoEnsaio, Pageable pageable) {
        return leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCase(codigoEnsaio, pageable)
                .map(l -> new LeituraColetaResponse(
                        l.getId(),
                        l.getPlanta().getParcela().getEnsaio().getCodigo(),
                        l.getCampanha(),
                        l.getPlanta().getPlantCode(),
                        l.getPlanta().getParcela().getClone().getCodigo(),
                        l.getPlanta().getParcela().getNumeroParcela(),
                        l.getPlanta().getNumeroPlanta(),
                        l.getQtdFrutos(),
                        l.getColetavel(),
                        l.getDiagnosticoBruto(),
                        l.getDataColetaStr(),
                        l.getLatitude(),
                        l.getLongitude(),
                        l.getAlertaGps(),
                        l.getPesquisadorEmail(),
                        l.getImportadoEm()
                ));
    }

    private Planta resolverOuCriarPlanta(Ensaio ensaio, LinhaColetaCsvDto item) {
        return plantaRepository.findByPlantCodeIgnoreCase(item.plantId())
                .orElseGet(() -> {
                    Clone clone = cloneRepository.findByCodigoIgnoreCase(item.cloneCodigo())
                            .orElseGet(() -> cloneRepository.save(new Clone(
                                    item.cloneCodigo().toUpperCase(),
                                    "Clone registrado via sincronização de campo",
                                    "Programa Genético CEPEC"
                            )));

                    String numParcela = (item.numeroParcela() == null || item.numeroParcela().isBlank()) ? "1" : item.numeroParcela();
                    Parcela parcela = parcelaRepository.findByEnsaioAndNumeroParcela(ensaio, numParcela)
                            .orElseGet(() -> parcelaRepository.save(new Parcela(ensaio, clone, 1, numParcela)));

                    return plantaRepository.findByParcelaAndNumeroPlanta(parcela, item.numeroPlanta())
                            .orElseGet(() -> {
                                SituacaoPlanta sit = item.coletavel() ? SituacaoPlanta.NORMAL : SituacaoPlanta.NAO_COLETAVEL;
                                Planta nova = new Planta(parcela, item.plantId(), item.numeroPlanta(), item.numeroPlanta(), sit);
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

    private String extrairEnsaioDaCampanha(String campanha) {
        if (campanha == null || campanha.isBlank()) {
            return "TC-1801";
        }
        return campanha.trim().toUpperCase();
    }
}
