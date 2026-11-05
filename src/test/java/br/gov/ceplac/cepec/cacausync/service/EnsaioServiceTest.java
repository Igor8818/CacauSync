package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.ProdutividadeCloneDto;
import br.gov.ceplac.cepec.cacausync.dto.RelatorioProdutividadeEnsaioResponse;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.repository.EnsaioRepository;
import br.gov.ceplac.cepec.cacausync.repository.LeituraColetaRepository;
import br.gov.ceplac.cepec.cacausync.repository.PlantaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnsaioServiceTest {

    @Mock
    private EnsaioRepository ensaioRepository;
    @Mock
    private PlantaRepository plantaRepository;
    @Mock
    private LeituraColetaRepository leituraColetaRepository;

    @InjectMocks
    private EnsaioService ensaioService;

    @Test
    @DisplayName("Deve calcular total de frutos, taxa de sobrevivência e média por planta viva de cada clone descontando plantas Não Coletáveis")
    void deveCalcularProdutividadePorCloneDescontandoPlantasNaoColetaveis() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio Competitivo TC-1801", "CEPEC");

        Clone ccn51 = new Clone("CCN-51", "Clone CCN-51", "CEPEC");
        Parcela parCcn51 = new Parcela(ensaio, ccn51, 1, "20");

        // CCN-51: 3 plantas no total, sendo 2 VIVAS (NORMAL) e 1 MORTA (NAO_COLETAVEL)
        Planta ccnPl1 = criarPlantaComId(1L, parCcn51, "CCN-51_par20_pla1", 1, SituacaoPlanta.NORMAL);
        Planta ccnPl2 = criarPlantaComId(2L, parCcn51, "CCN-51_par20_pla2", 2, SituacaoPlanta.NORMAL);
        Planta ccnPl3Morta = criarPlantaComId(3L, parCcn51, "CCN-51_par20_pla3", 3, SituacaoPlanta.NAO_COLETAVEL);

        Clone scp16005 = new Clone("SCP-16005", "Clone SCP-16005", "CEPEC");
        Parcela parScp = new Parcela(ensaio, scp16005, 1, "1");
        // SCP-16005: 2 plantas VIVAS
        Planta scpPl1 = criarPlantaComId(4L, parScp, "SCP-16005_par1_pla1", 1, SituacaoPlanta.NORMAL);
        Planta scpPl2 = criarPlantaComId(5L, parScp, "SCP-16005_par1_pla2", 2, SituacaoPlanta.NORMAL);

        // Leituras CCN-51: 20 frutos na planta 1 + 10 frutos na planta 2 = 30 frutos / 2 plantas vivas = média 15.00
        LeituraColeta l1 = criarLeitura(ccnPl1, 20, true, false);
        LeituraColeta l2 = criarLeitura(ccnPl2, 10, true, false);
        LeituraColeta l3Morta = criarLeitura(ccnPl3Morta, 0, false, true);

        // Leituras SCP-16005: 8 frutos + 12 frutos = 20 frutos / 2 plantas vivas = média 10.00
        LeituraColeta l4 = criarLeitura(scpPl1, 8, true, false);
        LeituraColeta l5 = criarLeitura(scpPl2, 12, true, false);

        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc("TC-1801"))
                .thenReturn(List.of(ccnPl1, ccnPl2, ccnPl3Morta, scpPl1, scpPl2));
        when(leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc("TC-1801"))
                .thenReturn(List.of(l1, l2, l3Morta, l4, l5));

        RelatorioProdutividadeEnsaioResponse relatorio = ensaioService.gerarRelatorioProdutividade("TC-1801");

        assertThat(relatorio.totalPlantasEnsaio()).isEqualTo(5);
        assertThat(relatorio.totalPlantasVivas()).isEqualTo(4);
        assertThat(relatorio.totalPlantasNaoColetaveis()).isEqualTo(1);
        assertThat(relatorio.totalFrutosAcumulados()).isEqualTo(50);
        assertThat(relatorio.leiturasComAlertaGps()).isEqualTo(1);
        assertThat(relatorio.rankingClones()).hasSize(2);

        // 1º lugar no ranking: CCN-51 (média 15.0 frutos por planta viva, sobrevivência 66.67%)
        ProdutividadeCloneDto primeiro = relatorio.rankingClones().get(0);
        assertThat(primeiro.codigoClone()).isEqualTo("CCN-51");
        assertThat(primeiro.totalFrutosContados()).isEqualTo(30);
        assertThat(primeiro.plantasVivasColetaveis()).isEqualTo(2);
        assertThat(primeiro.plantasNaoColetaveisOuMortas()).isEqualTo(1);
        assertThat(primeiro.taxaSobrevivenciaPercentual()).isEqualTo(66.67);
        assertThat(primeiro.mediaFrutosPorPlantaViva()).isEqualTo(15.0);

        // 2º lugar no ranking: SCP-16005 (média 10.0 frutos por planta viva, sobrevivência 100.0%)
        ProdutividadeCloneDto segundo = relatorio.rankingClones().get(1);
        assertThat(segundo.codigoClone()).isEqualTo("SCP-16005");
        assertThat(segundo.totalFrutosContados()).isEqualTo(20);
        assertThat(segundo.taxaSobrevivenciaPercentual()).isEqualTo(100.0);
        assertThat(segundo.mediaFrutosPorPlantaViva()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Deve exportar planilha CSV de rota no formato exato esperado pela TelaModoFieldBook do Android")
    void deveExportarRotaCsvParaFieldBookAndroid() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio TC-1801", "CEPEC");
        Clone clone = new Clone("SCP-16005", "Clone SCP", "CEPEC");
        Parcela parcela = new Parcela(ensaio, clone, 1, "1");
        Planta p1 = criarPlantaComId(1L, parcela, "SCP-16005_TC-1801_par1_pla1_c0226", 1, SituacaoPlanta.NORMAL);

        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc("TC-1801"))
                .thenReturn(List.of(p1));

        String csv = ensaioService.exportarRotaCsvParaFieldBook("TC-1801");

        assertThat(csv).startsWith("plant_id,Ensaio,Bloco,Parcela,Planta,Clone,Ordem,SitPla2023c,SitPla2023L\n");
        assertThat(csv).contains("SCP-16005_TC-1801_par1_pla1_c0226,TC-1801,1,1,1,SCP-16005,1,N11_P0_E0_M0_V0,nnnnnnnnnnn");
    }

    @Test
    @DisplayName("Validação do pesquisador manda no relatório: ajustada vale o número corrigido e rejeitada fica de fora")
    void relatorioRespeitaValidacaoDoPesquisador() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio TC-1801", "CEPEC");
        Clone clone = new Clone("CCN-51", "Clone CCN-51", "CEPEC");
        Parcela parcela = new Parcela(ensaio, clone, 1, "20");
        Planta p1 = criarPlantaComId(1L, parcela, "CCN-51_par20_pla1", 1, SituacaoPlanta.NORMAL);
        Planta p2 = criarPlantaComId(2L, parcela, "CCN-51_par20_pla2", 2, SituacaoPlanta.NORMAL);

        LeituraColeta ajustada = criarLeitura(p1, 10, true, false);   // a IA contou 10, o pesquisador corrigiu para 14
        ajustada.setStatusValidacao(StatusValidacao.AJUSTADA);
        ajustada.setQtdFrutosValidada(14);
        LeituraColeta rejeitada = criarLeitura(p2, 9, true, false);   // foto ruim: fora do relatório
        rejeitada.setStatusValidacao(StatusValidacao.REJEITADA);

        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc("TC-1801")).thenReturn(List.of(p1, p2));
        when(leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc("TC-1801"))
                .thenReturn(List.of(ajustada, rejeitada));

        RelatorioProdutividadeEnsaioResponse relatorio = ensaioService.gerarRelatorioProdutividade("TC-1801");

        assertThat(relatorio.totalFrutosAcumulados()).isEqualTo(14);
        assertThat(relatorio.leiturasRejeitadas()).isEqualTo(1);
        assertThat(relatorio.leiturasValidadas()).isEqualTo(1);
        assertThat(relatorio.leiturasPendentesValidacao()).isZero();
        ProdutividadeCloneDto clonePrimeiro = relatorio.rankingClones().get(0);
        assertThat(clonePrimeiro.totalFrutosContados()).isEqualTo(14);
        assertThat(clonePrimeiro.totalLeiturasRealizadas()).isEqualTo(1);
        // SPEC-003: o divisor é "planta viva AVALIADA" (p2 foi rejeitada, logo não conta): 14 frutos / 1 planta.
        assertThat(clonePrimeiro.mediaFrutosPorPlantaViva()).isEqualTo(14.0);
        assertThat(clonePrimeiro.plantasAvaliadas()).isEqualTo(1);
        assertThat(clonePrimeiro.coberturaPercentual()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("SPEC-003: fruto doente não infla a produtividade — o ranking e a estimativa partem dos frutos SADIOS")
    void produtividadeLiquidaUsaApenasFrutosSadios() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio", "CEPEC");
        Parcela parA = new Parcela(ensaio, new Clone("CLONE-A", "A", "CEPEC"), 1, "1");
        Parcela parB = new Parcela(ensaio, new Clone("CLONE-B", "B", "CEPEC"), 1, "2");
        Planta a1 = criarPlantaComId(1L, parA, "A_1", 1, SituacaoPlanta.NORMAL);
        Planta b1 = criarPlantaComId(2L, parB, "B_1", 1, SituacaoPlanta.NORMAL);

        // A: 10 frutos, mas 8 doentes (2 sadios). B: 6 frutos, todos sadios.
        LeituraColeta la = criarLeitura(a1, 10, true, false);
        la.setQtdSadios(2); la.setQtdVassoura(5); la.setQtdPodridao(3);
        LeituraColeta lb = criarLeitura(b1, 6, true, false);
        lb.setQtdSadios(6);

        mockEnsaio(ensaio, List.of(a1, b1), List.of(la, lb));

        RelatorioProdutividadeEnsaioResponse r = ensaioService.gerarRelatorioProdutividade("TC-1801");

        assertThat(r.rankingClones().get(0).codigoClone()).isEqualTo("CLONE-B");
        assertThat(r.rankingClones().get(1).codigoClone()).isEqualTo("CLONE-A");
        ProdutividadeCloneDto b = r.rankingClones().get(0);
        // 6 sadios/planta ÷ 22 frutos/kg × 1111 plantas/ha × 100% sobrevivência
        assertThat(b.estimativa().produtividadeRealKgHa()).isEqualTo(303.0);
        assertThat(b.estimativa().produtividadeRealArrobasHa()).isEqualTo(20.2);
        assertThat(b.estimativa().perdaDoencasKgHa()).isZero();
        ProdutividadeCloneDto a = r.rankingClones().get(1);
        assertThat(a.estimativa().produtividadeRealKgHa()).isEqualTo(101.0);   // 2 sadios
        assertThat(a.estimativa().perdaDoencasKgHa()).isEqualTo(404.0);        // 8 doentes
        assertThat(a.taxaInfeccaoPercentual()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("SPEC-003: só a leitura válida mais recente de cada planta entra (re-coleta não duplica) e a cobertura aparece")
    void recoletaNaoDuplicaECoberturaConsideraPlantasNaoVisitadas() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio", "CEPEC");
        Parcela par = new Parcela(ensaio, new Clone("CCN-51", "CCN", "CEPEC"), 1, "20");
        Planta p1 = criarPlantaComId(1L, par, "P1", 1, SituacaoPlanta.NORMAL);
        Planta p2 = criarPlantaComId(2L, par, "P2", 2, SituacaoPlanta.NORMAL);
        Planta p3 = criarPlantaComId(3L, par, "P3", 3, SituacaoPlanta.NORMAL);
        Planta p4 = criarPlantaComId(4L, par, "P4", 4, SituacaoPlanta.NORMAL);

        LeituraColeta maisRecente = criarLeitura(p1, 8, true, false);
        LeituraColeta antiga = criarLeitura(p1, 3, true, false);     // "REFAZER FOTO": ordem DESC => recente primeiro
        LeituraColeta l2 = criarLeitura(p2, 4, true, false);

        mockEnsaio(ensaio, List.of(p1, p2, p3, p4), List.of(maisRecente, antiga, l2));

        RelatorioProdutividadeEnsaioResponse r = ensaioService.gerarRelatorioProdutividade("TC-1801");

        ProdutividadeCloneDto clone = r.rankingClones().get(0);
        assertThat(clone.totalFrutosContados()).isEqualTo(12);          // 8 + 4, a leitura antiga (3) fica de fora
        assertThat(clone.plantasAvaliadas()).isEqualTo(2);
        assertThat(clone.coberturaPercentual()).isEqualTo(50.0);        // 2 de 4 plantas vivas visitadas
        assertThat(clone.mediaFrutosPorPlantaViva()).isEqualTo(6.0);    // 12 / 2 avaliadas (não 12 / 4)
        assertThat(clone.totalLeiturasRealizadas()).isEqualTo(3);
        assertThat(r.totalPlantasAvaliadas()).isEqualTo(2);
        assertThat(r.coberturaPercentual()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("SPEC-003: kg/ha ponderado pela sobrevivência e parâmetros agronômicos respeitados/validados")
    void estimativaConsideraSobrevivenciaEParametros() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio", "CEPEC");
        Parcela par = new Parcela(ensaio, new Clone("CCN-51", "CCN", "CEPEC"), 1, "20");
        Planta viva = criarPlantaComId(1L, par, "P1", 1, SituacaoPlanta.NORMAL);
        Planta morta = criarPlantaComId(2L, par, "P2", 2, SituacaoPlanta.NAO_COLETAVEL);
        LeituraColeta l = criarLeitura(viva, 10, true, false);
        l.setQtdSadios(10);
        l.setCampanha("Safra 2026");

        mockEnsaio(ensaio, List.of(viva, morta), List.of(l));

        RelatorioProdutividadeEnsaioResponse r = ensaioService.gerarRelatorioProdutividade("TC-1801", 20.0, 1000, null);

        assertThat(r.parametros().indiceFruto()).isEqualTo(20.0);
        assertThat(r.parametros().plantasPorHa()).isEqualTo(1000);
        assertThat(r.campanhasDisponiveis()).containsExactly("Safra 2026");
        // potencial = 10 / 20 × 1000 = 500 kg/ha; real = 500 × 50% sobrevivência = 250 kg/ha
        assertThat(r.estimativa().potencialKgHa()).isEqualTo(500.0);
        assertThat(r.estimativa().produtividadeRealKgHa()).isEqualTo(250.0);
        assertThat(r.estimativa().produtividadeRealArrobasHa()).isEqualTo(16.67);

        assertThatThrownBy(() -> ensaioService.gerarRelatorioProdutividade("TC-1801", 0.0, 1000, null))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> ensaioService.gerarRelatorioProdutividade("TC-1801", 22.0, 0, null))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("SPEC-003: filtro por campanha ignora leituras de outras campanhas")
    void filtroPorCampanha() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio", "CEPEC");
        Parcela par = new Parcela(ensaio, new Clone("CCN-51", "CCN", "CEPEC"), 1, "20");
        Planta p1 = criarPlantaComId(1L, par, "P1", 1, SituacaoPlanta.NORMAL);
        LeituraColeta nova = criarLeitura(p1, 9, true, false);
        nova.setCampanha("Safra 2026");
        LeituraColeta velha = criarLeitura(p1, 5, true, false);
        velha.setCampanha("Safra 2025");

        mockEnsaio(ensaio, List.of(p1), List.of(nova, velha));

        RelatorioProdutividadeEnsaioResponse r = ensaioService.gerarRelatorioProdutividade("TC-1801", null, null, "safra 2025");

        assertThat(r.totalFrutosAcumulados()).isEqualTo(5);
        assertThat(r.totalLeiturasRegistradas()).isEqualTo(2);   // SPEC-003 §2.5: contadores gerais ignoram o filtro
        assertThat(r.campanhasDisponiveis()).containsExactly("Safra 2025", "Safra 2026");
    }

    @Test
    @DisplayName("SPEC-003: campanhas diferentes só na caixa/espaços viram uma única opção e o filtro as agrupa")
    void campanhasComCaixaDiferenteSaoAgrupadas() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio", "CEPEC");
        Parcela par = new Parcela(ensaio, new Clone("CCN-51", "CCN", "CEPEC"), 1, "20");
        Planta p1 = criarPlantaComId(1L, par, "P1", 1, SituacaoPlanta.NORMAL);
        Planta p2 = criarPlantaComId(2L, par, "P2", 2, SituacaoPlanta.NORMAL);
        LeituraColeta a = criarLeitura(p1, 3, true, false);
        a.setCampanha("Safra");
        LeituraColeta b = criarLeitura(p2, 4, true, false);
        b.setCampanha(" safra ");

        mockEnsaio(ensaio, List.of(p1, p2), List.of(a, b));

        RelatorioProdutividadeEnsaioResponse r = ensaioService.gerarRelatorioProdutividade("TC-1801", null, null, "SAFRA");

        assertThat(r.campanhasDisponiveis()).hasSize(1);
        assertThat(r.totalFrutosAcumulados()).isEqualTo(7);
    }

    @Test
    @DisplayName("SPEC-003: taxa de infecção nunca passa de 100% mesmo com classes somando mais que o total")
    void taxaDeInfeccaoTemTeto() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio", "CEPEC");
        Parcela par = new Parcela(ensaio, new Clone("CCN-51", "CCN", "CEPEC"), 1, "20");
        Planta p1 = criarPlantaComId(1L, par, "P1", 1, SituacaoPlanta.NORMAL);
        LeituraColeta l = criarLeitura(p1, 4, true, false);   // IA contou 4, mas as classes dizem 7 doentes
        l.setQtdVassoura(4); l.setQtdPodridao(3);

        mockEnsaio(ensaio, List.of(p1), List.of(l));

        RelatorioProdutividadeEnsaioResponse r = ensaioService.gerarRelatorioProdutividade("TC-1801");

        assertThat(r.taxaInfeccaoEnsaioPercentual()).isEqualTo(100.0);
        assertThat(r.rankingClones().get(0).taxaInfeccaoPercentual()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("SPEC-003: leitura mais recente 'Não Coletável' zera a planta (não usa a leitura antiga)")
    void leituraMaisRecenteNaoColetavelZeraAPlanta() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio", "CEPEC");
        Parcela par = new Parcela(ensaio, new Clone("CCN-51", "CCN", "CEPEC"), 1, "20");
        Planta p1 = criarPlantaComId(1L, par, "P1", 1, SituacaoPlanta.NORMAL);
        LeituraColeta nova = criarLeitura(p1, 0, false, false);
        LeituraColeta antiga = criarLeitura(p1, 9, true, false);

        mockEnsaio(ensaio, List.of(p1), List.of(nova, antiga));

        ProdutividadeCloneDto clone = ensaioService.gerarRelatorioProdutividade("TC-1801").rankingClones().get(0);

        assertThat(clone.totalFrutosContados()).isZero();
        assertThat(clone.plantasAvaliadas()).isZero();
        assertThat(clone.estimativa().produtividadeRealKgHa()).isZero();
    }

    private void mockEnsaio(Ensaio ensaio, List<Planta> plantas, List<LeituraColeta> leituras) {
        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc("TC-1801")).thenReturn(plantas);
        when(leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc("TC-1801"))
                .thenReturn(leituras);
    }

    @Test
    @DisplayName("Deve calcular totais fitossanitários no relatório e exportar análise analítica CSV completa")
    void deveCalcularTotaisFitossanitariosEExportarAnaliseCompleta() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio TC-1801", "CEPEC");
        Clone clone = new Clone("CCN-51", "Clone CCN-51", "CEPEC");
        Parcela parcela = new Parcela(ensaio, clone, 1, "20");
        Planta p1 = criarPlantaComId(1L, parcela, "CCN-51_par20_pla1", 1, SituacaoPlanta.NORMAL);

        LeituraColeta l1 = criarLeitura(p1, 10, true, false);
        l1.setCampanha("Safra 2026");
        l1.setQtdSadios(6);
        l1.setQtdVassoura(2);
        l1.setQtdPodridao(1);
        l1.setQtdNaoAvaliavel(1);
        l1.setDataColetaStr("27/10 14:00");
        l1.setLatitude(-14.78);
        l1.setLongitude(-39.22);
        l1.setStatusValidacao(StatusValidacao.VALIDADA);
        l1.setValidadoPor("pesquisador@ceplac.gov.br");

        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(plantaRepository.findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc("TC-1801")).thenReturn(List.of(p1));
        when(leituraColetaRepository.findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc("TC-1801"))
                .thenReturn(List.of(l1));

        RelatorioProdutividadeEnsaioResponse relatorio = ensaioService.gerarRelatorioProdutividade("TC-1801");
        assertThat(relatorio.totalFrutosSadios()).isEqualTo(6);
        assertThat(relatorio.totalFrutosVassoura()).isEqualTo(2);
        assertThat(relatorio.totalFrutosPodridao()).isEqualTo(1);
        assertThat(relatorio.totalFrutosNaoAvaliavel()).isEqualTo(1);
        assertThat(relatorio.taxaInfeccaoEnsaioPercentual()).isEqualTo(30.0); // 3 doentes / 10 frutos = 30.0%

        String csvAnalise = ensaioService.exportarAnaliseCompletaCsv("TC-1801");
        assertThat(csvAnalise).startsWith("Ensaio,Campanha,Plant_ID,Clone,Parcela,Planta,Qtd_IA,Qtd_Validada,Qtd_Efetiva,Sadios,Vassoura,Podridao,Nao_Avaliavel,Status_Validacao,Validado_Por,Validado_Em,Observacao,Data_Coleta,Latitude,Longitude,Tem_Foto\n");
        assertThat(csvAnalise).contains("TC-1801,Safra 2026,CCN-51_par20_pla1,CCN-51,20,1,10,,10,6,2,1,1,VALIDADA,pesquisador@ceplac.gov.br");
    }

    private Planta criarPlantaComId(Long id, Parcela parcela, String code, int num, SituacaoPlanta situacao) {
        Planta p = new Planta(parcela, code, num, num, situacao);
        p.setId(id);
        return p;
    }

    private LeituraColeta criarLeitura(Planta planta, int frutos, boolean coletavel, boolean alertaGps) {
        LeituraColeta l = new LeituraColeta();
        l.setPlanta(planta);
        l.setQtdFrutos(frutos);
        l.setColetavel(coletavel);
        l.setDiagnosticoBruto(coletavel ? String.valueOf(frutos) : "Não Coletável");
        l.setAlertaGps(alertaGps);
        return l;
    }
}
