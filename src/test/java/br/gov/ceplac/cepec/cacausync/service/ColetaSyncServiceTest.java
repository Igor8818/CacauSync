package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoImportacaoCsvResponse;
import br.gov.ceplac.cepec.cacausync.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ColetaSyncServiceTest {

    @Mock
    private CsvColetaParser csvColetaParser;
    @Mock
    private EnsaioRepository ensaioRepository;
    @Mock
    private CloneRepository cloneRepository;
    @Mock
    private ParcelaRepository parcelaRepository;
    @Mock
    private PlantaRepository plantaRepository;
    @Mock
    private LeituraColetaRepository leituraColetaRepository;

    @InjectMocks
    private ColetaSyncService coletaSyncService;

    @Test
    @DisplayName("Deve importar coletas, atualizar planta para NAO_COLETAVEL quando morta/ausente e sinalizar alerta de GPS zerado")
    void deveImportarColetasAtualizandoMortalidadeEAuditandoGps() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio TC-1801", "CEPEC");
        Clone clone = new Clone("SCP-16005", "Clone SCP", "CEPEC");
        Parcela parcela = new Parcela(ensaio, clone, 1, "1");
        Planta plantaViva = new Planta(parcela, "SCP-16005_P1_Pl1", 1, 1, SituacaoPlanta.NORMAL);
        Planta plantaQueMorreu = new Planta(parcela, "SCP-16005_P1_Pl2", 2, 2, SituacaoPlanta.NORMAL);

        LinhaColetaCsvDto leituraNormal = new LinhaColetaCsvDto(
                "SAFRA_2026", "SCP-16005_P1_Pl1", "SCP-16005", "1", 1,
                "15", 15, true, "27/10 10:00", -14.789, -39.278
        );
        LinhaColetaCsvDto leituraNaoColetavelSemGps = new LinhaColetaCsvDto(
                "SAFRA_2026", "SCP-16005_P1_Pl2", "SCP-16005", "1", 2,
                "Não Coletável", 0, false, "27/10 10:05", 0.0, 0.0
        );

        when(csvColetaParser.parse(any(InputStream.class)))
                .thenReturn(List.of(leituraNormal, leituraNaoColetavelSemGps));
        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(leituraColetaRepository.existsByChaveIdempotencia(anyString())).thenReturn(false);
        when(plantaRepository.findByPlantCodeIgnoreCase("SCP-16005_P1_Pl1")).thenReturn(Optional.of(plantaViva));
        when(plantaRepository.findByPlantCodeIgnoreCase("SCP-16005_P1_Pl2")).thenReturn(Optional.of(plantaQueMorreu));

        ResultadoImportacaoCsvResponse resp = coletaSyncService.importarCsv(
                "TC-1801",
                new ByteArrayInputStream("mock".getBytes(StandardCharsets.UTF_8)),
                "Coleta_Custom_123.csv",
                "pesquisador@cepec.gov.br"
        );

        assertThat(resp.totalLinhasProcessadas()).isEqualTo(2);
        assertThat(resp.leiturasNovasInseridas()).isEqualTo(2);
        assertThat(resp.leiturasDuplicadasIgnoradas()).isZero();
        assertThat(resp.plantasMarcadasNaoColetaveis()).isEqualTo(1);
        assertThat(resp.alertasGpsZerado()).isEqualTo(1);
        assertThat(resp.totalFrutosImportadosLote()).isEqualTo(15);
        assertThat(plantaQueMorreu.getSituacao()).isEqualTo(SituacaoPlanta.NAO_COLETAVEL);

        ArgumentCaptor<LeituraColeta> captor = ArgumentCaptor.forClass(LeituraColeta.class);
        verify(leituraColetaRepository, times(2)).save(captor.capture());
        List<LeituraColeta> salvas = captor.getAllValues();
        assertThat(salvas.get(0).getAlertaGps()).isFalse();
        assertThat(salvas.get(1).getAlertaGps()).isTrue();
    }

    @Test
    @DisplayName("Deve garantir idempotência ignorando leituras já sincronizadas quando o pesquisador envia o mesmo CSV duas vezes")
    void deveIgnorarLeiturasDuplicadasGarantindoIdempotencia() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio TC-1801", "CEPEC");
        LinhaColetaCsvDto leituraDuplicada = new LinhaColetaCsvDto(
                "SAFRA_2026", "CCN-51_P20_Pl1", "CCN-51", "20", 1,
                "22", 22, true, "27/10 11:00", -14.789, -39.278
        );

        when(csvColetaParser.parse(any(InputStream.class))).thenReturn(List.of(leituraDuplicada));
        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(leituraColetaRepository.existsByChaveIdempotencia(anyString())).thenReturn(true);

        ResultadoImportacaoCsvResponse resp = coletaSyncService.importarCsv(
                "TC-1801",
                new ByteArrayInputStream("mock".getBytes(StandardCharsets.UTF_8)),
                "Coleta_Repetida.csv",
                "pesquisador@cepec.gov.br"
        );

        assertThat(resp.totalLinhasProcessadas()).isEqualTo(1);
        assertThat(resp.leiturasNovasInseridas()).isZero();
        assertThat(resp.leiturasDuplicadasIgnoradas()).isEqualTo(1);
        verify(leituraColetaRepository, never()).save(any());
    }
}
