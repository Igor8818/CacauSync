package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.CriarEnsaioRequest;
import br.gov.ceplac.cepec.cacausync.dto.EnsaioResumoResponse;
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
        Ensaio ensaio = ensaioRepository.findByCodigoIgnoreCase(codigoEnsaio)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ensaio genético não encontrado: " + codigoEnsaio));

        List<Planta> plantas = plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc(ensaio.getCodigo());
        List<LeituraColeta> leituras = leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(ensaio.getCodigo());

        Map<Long, List<LeituraColeta>> leiturasPorPlantaId = leituras.stream()
                .collect(Collectors.groupingBy(l -> l.getPlanta().getId()));

        Map<String, List<Planta>> plantasPorClone = plantas.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getParcela().getClone().getCodigo(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ProdutividadeCloneDto> ranking = new ArrayList<>();

        for (Map.Entry<String, List<Planta>> entry : plantasPorClone.entrySet()) {
            String codigoClone = entry.getKey();
            List<Planta> plantasDoClone = entry.getValue();
            String descricaoClone = plantasDoClone.get(0).getParcela().getClone().getDescricao();

            int totalPlantasClone = plantasDoClone.size();
            int plantasVivas = (int) plantasDoClone.stream()
                    .filter(p -> p.getSituacao() == SituacaoPlanta.NORMAL)
                    .count();
            int plantasNaoColetaveis = totalPlantasClone - plantasVivas;

            double taxaSobrevivencia = totalPlantasClone > 0
                    ? arredondar((plantasVivas * 100.0) / totalPlantasClone)
                    : 0.0;

            int totalLeiturasClone = 0;
            int totalFrutosClone = 0;
            int totalSadiosClone = 0;
            int totalVassouraClone = 0;
            int totalPodridaoClone = 0;
            int totalNaoAvalClone = 0;

            for (Planta p : plantasDoClone) {
                List<LeituraColeta> leiturasPlanta = leiturasPorPlantaId.getOrDefault(p.getId(), List.of());
                for (LeituraColeta l : leiturasPlanta) {
                    // Leitura rejeitada pelo pesquisador fica fora; ajustada vale o numero corrigido.
                    if (l.getStatusValidacao() != StatusValidacao.REJEITADA) {
                        totalLeiturasClone++;
                    }
                    if (l.contaNoRelatorio()) {
                        totalFrutosClone += l.qtdEfetiva();
                        totalSadiosClone += l.qtdSadiosEfetiva();
                        totalVassouraClone += l.qtdVassouraEfetiva();
                        totalPodridaoClone += l.qtdPodridaoEfetiva();
                        totalNaoAvalClone += l.qtdNaoAvaliavelEfetiva();
                    }
                }
            }

            double mediaFrutosPorPlantaViva = plantasVivas > 0
                    ? arredondar((double) totalFrutosClone / plantasVivas)
                    : 0.0;
            int doentesClone = totalVassouraClone + totalPodridaoClone;
            double taxaInfeccaoClone = totalFrutosClone > 0
                    ? arredondar((doentesClone * 100.0) / totalFrutosClone)
                    : 0.0;

            ranking.add(new ProdutividadeCloneDto(
                    codigoClone,
                    descricaoClone,
                    totalPlantasClone,
                    plantasVivas,
                    plantasNaoColetaveis,
                    taxaSobrevivencia,
                    totalLeiturasClone,
                    totalFrutosClone,
                    totalSadiosClone,
                    totalVassouraClone,
                    totalPodridaoClone,
                    totalNaoAvalClone,
                    taxaInfeccaoClone,
                    mediaFrutosPorPlantaViva
            ));
        }

        ranking.sort(Comparator.comparingDouble(ProdutividadeCloneDto::mediaFrutosPorPlantaViva).reversed()
                .thenComparing(Comparator.comparingInt(ProdutividadeCloneDto::totalFrutosContados).reversed()));

        int totalVivasEnsaio = (int) plantas.stream().filter(p -> p.getSituacao() == SituacaoPlanta.NORMAL).count();
        int totalFrutosEnsaio = leituras.stream()
                .filter(LeituraColeta::contaNoRelatorio)
                .mapToInt(LeituraColeta::qtdEfetiva)
                .sum();
        int totalSadiosEnsaio = leituras.stream()
                .filter(LeituraColeta::contaNoRelatorio)
                .mapToInt(LeituraColeta::qtdSadiosEfetiva)
                .sum();
        int totalVassouraEnsaio = leituras.stream()
                .filter(LeituraColeta::contaNoRelatorio)
                .mapToInt(LeituraColeta::qtdVassouraEfetiva)
                .sum();
        int totalPodridaoEnsaio = leituras.stream()
                .filter(LeituraColeta::contaNoRelatorio)
                .mapToInt(LeituraColeta::qtdPodridaoEfetiva)
                .sum();
        int totalNaoAvalEnsaio = leituras.stream()
                .filter(LeituraColeta::contaNoRelatorio)
                .mapToInt(LeituraColeta::qtdNaoAvaliavelEfetiva)
                .sum();
        int doentesEnsaio = totalVassouraEnsaio + totalPodridaoEnsaio;
        double taxaInfeccaoEnsaio = totalFrutosEnsaio > 0
                ? arredondar((doentesEnsaio * 100.0) / totalFrutosEnsaio)
                : 0.0;

        int alertasGps = (int) leituras.stream().filter(l -> Boolean.TRUE.equals(l.getAlertaGps())).count();
        int pendentes = (int) leituras.stream().filter(l -> l.getStatusValidacao() == StatusValidacao.PENDENTE).count();
        int rejeitadas = (int) leituras.stream().filter(l -> l.getStatusValidacao() == StatusValidacao.REJEITADA).count();
        int validadas = leituras.size() - pendentes - rejeitadas;

        return new RelatorioProdutividadeEnsaioResponse(
                ensaio.getCodigo(),
                ensaio.getNome(),
                ensaio.getEstacaoExperimental(),
                plantas.size(),
                totalVivasEnsaio,
                plantas.size() - totalVivasEnsaio,
                leituras.size(),
                totalFrutosEnsaio,
                totalSadiosEnsaio,
                totalVassouraEnsaio,
                totalPodridaoEnsaio,
                totalNaoAvalEnsaio,
                taxaInfeccaoEnsaio,
                alertasGps,
                pendentes,
                validadas,
                rejeitadas,
                LocalDateTime.now(),
                ranking
        );
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
                    .append(l.getQtdSadios()).append(',')
                    .append(l.getQtdVassoura()).append(',')
                    .append(l.getQtdPodridao()).append(',')
                    .append(l.getQtdNaoAvaliavel()).append(',')
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
