package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.CriarEnsaioRequest;
import br.gov.ceplac.cepec.cacausync.dto.EnsaioResumoResponse;
import br.gov.ceplac.cepec.cacausync.dto.EstimativaAgronomicaDto;
import br.gov.ceplac.cepec.cacausync.dto.ParametrosAgronomicosDto;
import br.gov.ceplac.cepec.cacausync.dto.ProdutividadeCloneDto;
import br.gov.ceplac.cepec.cacausync.dto.RelatorioProdutividadeEnsaioResponse;
import br.gov.ceplac.cepec.cacausync.exception.RecursoNaoEncontradoException;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.repository.EnsaioRepository;
import br.gov.ceplac.cepec.cacausync.repository.LeituraColetaRepository;
import br.gov.ceplac.cepec.cacausync.repository.PlantaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EnsaioService {

    /** 1 arroba = 15 kg. */
    private static final double KG_POR_ARROBA = 15.0;

    private final EnsaioRepository ensaioRepository;
    private final PlantaRepository plantaRepository;
    private final LeituraColetaRepository leituraColetaRepository;

    public EnsaioService(
            EnsaioRepository ensaioRepository,
            PlantaRepository plantaRepository,
            LeituraColetaRepository leituraColetaRepository
    ) {
        this.ensaioRepository = ensaioRepository;
        this.plantaRepository = plantaRepository;
        this.leituraColetaRepository = leituraColetaRepository;
    }

    @Transactional(readOnly = true)
    public List<EnsaioResumoResponse> listarEnsaios() {
        return ensaioRepository.findAll().stream()
                .map(e -> {
                    int totalPlantas = plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc(e.getCodigo()).size();
                    int totalLeituras = leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(e.getCodigo()).size();
                    return new EnsaioResumoResponse(
                            e.getId(),
                            e.getCodigo(),
                            e.getNome(),
                            e.getEstacaoExperimental(),
                            e.getStatus(),
                            totalPlantas,
                            totalLeituras,
                            e.getCriadoEm()
                    );
                })
                .toList();
    }

    @Transactional
    public EnsaioResumoResponse criarEnsaio(CriarEnsaioRequest request) {
        String codigo = request.codigo().trim().toUpperCase();
        if (ensaioRepository.findByCodigoIgnoreCase(codigo).isPresent()) {
            throw new RegraNegocioException("Já existe um ensaio genético cadastrado com o código: " + codigo);
        }
        String estacao = (request.estacaoExperimental() != null && !request.estacaoExperimental().isBlank())
                ? request.estacaoExperimental().trim()
                : "CEPEC/CEPLAC - Ilhéus/Itabuna, BA";

        Ensaio salvo = ensaioRepository.save(new Ensaio(codigo, request.nome().trim(), estacao));
        return new EnsaioResumoResponse(
                salvo.getId(),
                salvo.getCodigo(),
                salvo.getNome(),
                salvo.getEstacaoExperimental(),
                salvo.getStatus(),
                0,
                0,
                salvo.getCriadoEm()
        );
    }

    @Transactional(readOnly = true)
    public RelatorioProdutividadeEnsaioResponse gerarRelatorioProdutividade(String codigoEnsaio) {
        return gerarRelatorioProdutividade(codigoEnsaio, null, null, null);
    }

    /**
     * Relatório de produtividade conforme SPEC-003: cada planta conta uma única vez (leitura válida mais recente),
     * a média usa só plantas vivas avaliadas e a estimativa agronômica parte dos frutos SADIOS.
     */
    @Transactional(readOnly = true)
    public RelatorioProdutividadeEnsaioResponse gerarRelatorioProdutividade(
            String codigoEnsaio, Double indiceFruto, Integer plantasPorHa, String campanha
    ) {
        double indice = indiceFruto != null ? indiceFruto : ParametrosAgronomicosDto.INDICE_FRUTO_PADRAO;
        int densidade = plantasPorHa != null ? plantasPorHa : ParametrosAgronomicosDto.PLANTAS_POR_HA_PADRAO;
        if (!(indice > 0 && indice <= 200)) {
            throw new RegraNegocioException("O índice de fruto deve estar entre 0 (exclusivo) e 200 frutos por kg.");
        }
        if (densidade < 1 || densidade > 10000) {
            throw new RegraNegocioException("A densidade de plantio deve estar entre 1 e 10000 plantas por hectare.");
        }
        String filtroCampanha = (campanha == null || campanha.isBlank()) ? null : campanha.trim();

        Ensaio ensaio = ensaioRepository.findByCodigoIgnoreCase(codigoEnsaio)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ensaio genético não encontrado: " + codigoEnsaio));

        List<Planta> plantas = plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc(ensaio.getCodigo());
        List<LeituraColeta> todasLeituras = leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(ensaio.getCodigo());

        Map<String, String> campanhasUnicas = new TreeMap<>();
        for (LeituraColeta l : todasLeituras) {
            if (l.getCampanha() != null && !l.getCampanha().isBlank()) {
                campanhasUnicas.putIfAbsent(l.getCampanha().trim().toLowerCase(Locale.ROOT), l.getCampanha().trim());
            }
        }
        List<String> campanhasDisponiveis = List.copyOf(campanhasUnicas.values());

        List<LeituraColeta> leiturasFiltradas = filtroCampanha == null
                ? todasLeituras
                : todasLeituras.stream()
                        .filter(l -> l.getCampanha() != null && filtroCampanha.equalsIgnoreCase(l.getCampanha().trim()))
                        .toList();

        // Leitura válida por planta: a mais recente que não foi rejeitada (lista já vem em importadoEm DESC).
        Map<Long, LeituraColeta> leituraValidaPorPlanta = new HashMap<>();
        Map<Long, Integer> leiturasNaoRejeitadasPorPlanta = new HashMap<>();
        for (LeituraColeta l : leiturasFiltradas) {
            if (l.getStatusValidacao() == StatusValidacao.REJEITADA) {
                continue;
            }
            Long plantaId = l.getPlanta().getId();
            leituraValidaPorPlanta.putIfAbsent(plantaId, l);
            leiturasNaoRejeitadasPorPlanta.merge(plantaId, 1, Integer::sum);
        }

        Map<String, List<Planta>> plantasPorClone = plantas.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getParcela().getClone().getCodigo(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ProdutividadeCloneDto> ranking = new ArrayList<>();
        int ensaioVivas = 0, ensaioAvaliadas = 0;
        int ensaioFrutos = 0, ensaioSadios = 0, ensaioVassoura = 0, ensaioPodridao = 0, ensaioNaoAval = 0;

        for (Map.Entry<String, List<Planta>> entry : plantasPorClone.entrySet()) {
            String codigoClone = entry.getKey();
            List<Planta> plantasDoClone = entry.getValue();
            String descricaoClone = plantasDoClone.get(0).getParcela().getClone().getDescricao();

            int totalPlantasClone = plantasDoClone.size();
            int plantasVivas = 0;
            int plantasAvaliadas = 0;
            int totalLeiturasClone = 0;
            int totalFrutosClone = 0, totalSadiosClone = 0, totalVassouraClone = 0, totalPodridaoClone = 0, totalNaoAvalClone = 0;

            for (Planta p : plantasDoClone) {
                totalLeiturasClone += leiturasNaoRejeitadasPorPlanta.getOrDefault(p.getId(), 0);
                if (p.getSituacao() != SituacaoPlanta.NORMAL) {
                    continue;
                }
                plantasVivas++;
                LeituraColeta valida = leituraValidaPorPlanta.get(p.getId());
                if (valida != null && valida.contaNoRelatorio()) {
                    plantasAvaliadas++;
                    totalFrutosClone += valida.qtdEfetiva();
                    totalSadiosClone += valida.qtdSadiosEfetiva();
                    totalVassouraClone += valida.qtdVassouraEfetiva();
                    totalPodridaoClone += valida.qtdPodridaoEfetiva();
                    totalNaoAvalClone += valida.qtdNaoAvaliavelEfetiva();
                }
            }

            double taxaSobrevivencia = percentual(plantasVivas, totalPlantasClone);
            double mediaFrutosPorPlantaViva = plantasAvaliadas > 0
                    ? arredondar((double) totalFrutosClone / plantasAvaliadas)
                    : 0.0;
            int doentesClone = totalVassouraClone + totalPodridaoClone;
            double taxaInfeccaoClone = percentual(doentesClone, totalFrutosClone);

            EstimativaAgronomicaDto estimativa = estimar(
                    totalSadiosClone, doentesClone, plantasAvaliadas, taxaSobrevivencia, indice, densidade);

            ranking.add(new ProdutividadeCloneDto(
                    codigoClone, descricaoClone, totalPlantasClone, plantasVivas,
                    totalPlantasClone - plantasVivas, taxaSobrevivencia, totalLeiturasClone,
                    totalFrutosClone, totalSadiosClone, totalVassouraClone, totalPodridaoClone, totalNaoAvalClone,
                    taxaInfeccaoClone, mediaFrutosPorPlantaViva,
                    plantasAvaliadas, percentual(plantasAvaliadas, plantasVivas), estimativa
            ));

            ensaioVivas += plantasVivas;
            ensaioAvaliadas += plantasAvaliadas;
            ensaioFrutos += totalFrutosClone;
            ensaioSadios += totalSadiosClone;
            ensaioVassoura += totalVassouraClone;
            ensaioPodridao += totalPodridaoClone;
            ensaioNaoAval += totalNaoAvalClone;
        }

        ranking.sort(Comparator
                .comparingDouble((ProdutividadeCloneDto c) -> c.estimativa().mediaSadiosPorPlantaAvaliada()).reversed()
                .thenComparing(Comparator.comparingInt(ProdutividadeCloneDto::totalFrutosSadios).reversed()));

        int doentesEnsaio = ensaioVassoura + ensaioPodridao;
        double taxaInfeccaoEnsaio = percentual(doentesEnsaio, ensaioFrutos);
        double sobrevivenciaEnsaio = percentual(ensaioVivas, plantas.size());
        EstimativaAgronomicaDto estimativaEnsaio = estimar(
                ensaioSadios, doentesEnsaio, ensaioAvaliadas, sobrevivenciaEnsaio, indice, densidade);

        // SPEC-003 §2.5: contadores gerais sempre sobre TODAS as leituras do ensaio (o filtro vale só para a produtividade).
        List<LeituraColeta> leituras = todasLeituras;
        int alertasGps = (int) leituras.stream().filter(l -> Boolean.TRUE.equals(l.getAlertaGps())).count();
        int pendentes = (int) leituras.stream().filter(l -> l.getStatusValidacao() == StatusValidacao.PENDENTE).count();
        int rejeitadas = (int) leituras.stream().filter(l -> l.getStatusValidacao() == StatusValidacao.REJEITADA).count();
        int validadas = leituras.size() - pendentes - rejeitadas;

        return new RelatorioProdutividadeEnsaioResponse(
                ensaio.getCodigo(),
                ensaio.getNome(),
                ensaio.getEstacaoExperimental(),
                plantas.size(),
                ensaioVivas,
                plantas.size() - ensaioVivas,
                leituras.size(),
                ensaioFrutos,
                ensaioSadios,
                ensaioVassoura,
                ensaioPodridao,
                ensaioNaoAval,
                taxaInfeccaoEnsaio,
                alertasGps,
                pendentes,
                validadas,
                rejeitadas,
                LocalDateTime.now(),
                ranking,
                ensaioAvaliadas,
                percentual(ensaioAvaliadas, ensaioVivas),
                estimativaEnsaio,
                new ParametrosAgronomicosDto(indice, densidade, filtroCampanha),
                campanhasDisponiveis
        );
    }

    /** Estimativa indicativa kg/ha e @/ha a partir dos frutos sadios (SPEC-003 §2.3). */
    private EstimativaAgronomicaDto estimar(
            int sadios, int doentes, int plantasAvaliadas, double taxaSobrevivenciaPercentual,
            double indiceFruto, int plantasPorHa
    ) {
        if (plantasAvaliadas <= 0) {
            return EstimativaAgronomicaDto.zerada();
        }
        double mediaSadios = (double) sadios / plantasAvaliadas;
        double potencialKgHa = mediaSadios / indiceFruto * plantasPorHa;
        double realKgHa = potencialKgHa * taxaSobrevivenciaPercentual / 100.0;
        double perdaKgHa = ((double) doentes / plantasAvaliadas) / indiceFruto * plantasPorHa
                * taxaSobrevivenciaPercentual / 100.0;
        return new EstimativaAgronomicaDto(
                arredondar(mediaSadios),
                arredondar(potencialKgHa),
                arredondar(realKgHa),
                arredondar(realKgHa / KG_POR_ARROBA),
                arredondar(perdaKgHa),
                arredondar(perdaKgHa / KG_POR_ARROBA)
        );
    }

    private double percentual(int parte, int total) {
        return total > 0 ? Math.min(100.0, arredondar((parte * 100.0) / total)) : 0.0;
    }

    /**
     * Exporta a planilha de planejamento pré-campo exatamente no formato esperado pela
     * TelaModoFieldBook (MainActivity.kt) e dados_cacau.csv do aplicativo Android:
     * plant_id,Ensaio,Bloco,Parcela,Planta,Clone,Ordem,SitPla2023c,SitPla2023L
     */
    @Transactional(readOnly = true)
    public String exportarRotaCsvParaFieldBook(String codigoEnsaio) {
        Ensaio ensaio = ensaioRepository.findByCodigoIgnoreCase(codigoEnsaio)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ensaio genético não encontrado: " + codigoEnsaio));

        List<Planta> plantas = plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc(ensaio.getCodigo());
        StringBuilder csv = new StringBuilder();
        csv.append("plant_id,Ensaio,Bloco,Parcela,Planta,Clone,Ordem,SitPla2023c,SitPla2023L\n");

        for (Planta p : plantas) {
            Parcela parcela = p.getParcela();
            Clone clone = parcela.getClone();
            csv.append(p.getPlantCode()).append(',')
                    .append(ensaio.getCodigo()).append(',')
                    .append(parcela.getBloco()).append(',')
                    .append(parcela.getNumeroParcela()).append(',')
                    .append(p.getNumeroPlanta()).append(',')
                    .append(clone.getCodigo()).append(',')
                    .append(p.getOrdemRota()).append(',')
                    .append(p.getSitPlaConsolidado() != null ? p.getSitPlaConsolidado() : "N11_P0_E0_M0_V0").append(',')
                    .append(p.getSitPlaHistorico() != null ? p.getSitPlaHistorico() : "nnnnnnnnnnn")
                    .append('\n');
        }
        return csv.toString();
    }

    /**
     * Exporta a planilha analítica consolidada pós-auditoria com detalhamento fitossanitário e validações.
     */
    @Transactional(readOnly = true)
    public String exportarAnaliseCompletaCsv(String codigoEnsaio) {
        Ensaio ensaio = ensaioRepository.findByCodigoIgnoreCase(codigoEnsaio)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ensaio genético não encontrado: " + codigoEnsaio));

        List<LeituraColeta> leituras = leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(ensaio.getCodigo());
        StringBuilder csv = new StringBuilder();
        csv.append("Ensaio,Campanha,Plant_ID,Clone,Parcela,Planta,Qtd_IA,Qtd_Validada,Qtd_Efetiva,Sadios,Vassoura,Podridao,Nao_Avaliavel,Status_Validacao,Validado_Por,Validado_Em,Observacao,Data_Coleta,Latitude,Longitude,Tem_Foto\n");

        for (LeituraColeta l : leituras) {
            csv.append(ensaio.getCodigo()).append(',')
                    .append(csvField(l.getCampanha())).append(',')
                    .append(csvField(l.getPlanta().getPlantCode())).append(',')
                    .append(csvField(l.getPlanta().getParcela().getClone().getCodigo())).append(',')
                    .append(csvField(l.getPlanta().getParcela().getNumeroParcela())).append(',')
                    .append(l.getPlanta().getNumeroPlanta()).append(',')
                    .append(l.getQtdFrutos()).append(',')
                    .append(l.getQtdFrutosValidada() != null ? l.getQtdFrutosValidada() : "").append(',')
                    .append(l.qtdEfetiva()).append(',')
                    .append(l.qtdSadiosEfetiva()).append(',')
                    .append(l.qtdVassouraEfetiva()).append(',')
                    .append(l.qtdPodridaoEfetiva()).append(',')
                    .append(l.qtdNaoAvaliavelEfetiva()).append(',')
                    .append(l.getStatusValidacao().name()).append(',')
                    .append(csvField(l.getValidadoPor())).append(',')
                    .append(l.getValidadoEm() != null ? l.getValidadoEm().toString() : "").append(',')
                    .append(csvField(l.getObservacaoValidacao())).append(',')
                    .append(csvField(l.getDataColetaStr())).append(',')
                    .append(l.getLatitude()).append(',')
                    .append(l.getLongitude()).append(',')
                    .append(l.temFoto())
                    .append('\n');
        }
        return csv.toString();
    }

    private String csvField(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private double arredondar(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
