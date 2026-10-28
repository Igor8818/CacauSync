package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.ProdutividadeCloneDto;
import br.gov.ceplac.cepec.cacausync.dto.RelatorioProdutividadeEnsaioResponse;
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
        assertThat(clonePrimeiro.mediaFrutosPorPlantaViva()).isEqualTo(7.0);
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
