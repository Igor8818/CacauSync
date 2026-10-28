package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.ArquivoFoto;
import br.gov.ceplac.cepec.cacausync.dto.DeteccaoDto;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoImportacaoCsvResponse;
import br.gov.ceplac.cepec.cacausync.dto.SyncJsonRequest;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
    @Mock
    private FotoStorageService fotoStorageService;
    @Spy
    private FotoMatcher fotoMatcher = new FotoMatcher();
    @Spy
    private LeituraMapper leituraMapper = new LeituraMapper(new ObjectMapper());

    @InjectMocks
    private ColetaSyncService coletaSyncService;

    private final Ensaio ensaio = new Ensaio("TC-1801", "Ensaio TC-1801", "CEPEC");
    private final Clone clone = new Clone("SCP-16005", "Clone SCP", "CEPEC");
    private final Parcela parcela = new Parcela(ensaio, clone, 1, "1");

    @Test
    @DisplayName("Deve importar coletas, atualizar planta para NAO_COLETAVEL quando morta/ausente e sinalizar alerta de GPS zerado")
    void deveImportarColetasAtualizandoMortalidadeEAuditandoGps() {
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
        when(leituraColetaRepository.findByChaveIdempotencia(anyString())).thenReturn(Optional.empty());
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
        assertThat(resp.fotosAnexadas()).isZero();
        assertThat(plantaQueMorreu.getSituacao()).isEqualTo(SituacaoPlanta.NAO_COLETAVEL);

        ArgumentCaptor<LeituraColeta> captor = ArgumentCaptor.forClass(LeituraColeta.class);
        verify(leituraColetaRepository, times(2)).save(captor.capture());
        List<LeituraColeta> salvas = captor.getAllValues();
        assertThat(salvas.get(0).getAlertaGps()).isFalse();
        assertThat(salvas.get(1).getAlertaGps()).isTrue();
        assertThat(salvas.get(0).getStatusValidacao()).isEqualTo(StatusValidacao.PENDENTE);
    }

    @Test
    @DisplayName("Deve garantir idempotência ignorando leituras já sincronizadas quando o pesquisador envia o mesmo CSV duas vezes")
    void deveIgnorarLeiturasDuplicadasGarantindoIdempotencia() {
        LinhaColetaCsvDto leituraDuplicada = new LinhaColetaCsvDto(
                "SAFRA_2026", "CCN-51_P20_Pl1", "CCN-51", "20", 1,
                "22", 22, true, "27/10 11:00", -14.789, -39.278
        );

        when(csvColetaParser.parse(any(InputStream.class))).thenReturn(List.of(leituraDuplicada));
        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(leituraColetaRepository.findByChaveIdempotencia(anyString())).thenReturn(Optional.of(new LeituraColeta()));

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

    @Test
    @DisplayName("Deve anexar a foto a uma leitura já importada antes (CSV enviado primeiro, fotos depois) sem duplicar a leitura")
    void deveAnexarFotoEmLeituraJaImportadaSemDuplicar() {
        LinhaColetaCsvDto linha = new LinhaColetaCsvDto(
                "SAFRA_2026", "CCN-51_P20_Pl1", "CCN-51", "20", 1,
                "22", 22, true, "27/10 11:00", -14.789, -39.278
        );
        LeituraColeta jaImportada = new LeituraColeta();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

        when(csvColetaParser.parse(any(InputStream.class))).thenReturn(List.of(linha));
        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(leituraColetaRepository.findByChaveIdempotencia(anyString())).thenReturn(Optional.of(jaImportada));
        when(fotoStorageService.salvar(jpeg))
                .thenReturn(new FotoStorageService.FotoSalva("11111111-2222-3333-4444-555555555555.jpg", "image/jpeg"));

        ResultadoImportacaoCsvResponse resp = coletaSyncService.importarCsv(
                "TC-1801",
                new ByteArrayInputStream("mock".getBytes(StandardCharsets.UTF_8)),
                "Coleta.csv",
                "pesquisador@cepec.gov.br",
                List.of(new ArquivoFoto("CCN-51_P20_Pl1_1761693000123.jpg", jpeg))
        );

        assertThat(resp.leiturasNovasInseridas()).isZero();
        assertThat(resp.leiturasDuplicadasIgnoradas()).isEqualTo(1);
        assertThat(resp.fotosAnexadas()).isEqualTo(1);
        assertThat(resp.fotosIgnoradas()).isZero();
        assertThat(jaImportada.temFoto()).isTrue();
        verify(leituraColetaRepository, times(1)).save(jaImportada);
    }

    @Test
    @DisplayName("Uma foto inválida não derruba a importação: a leitura é salva sem foto e o aviso é registrado")
    void fotoInvalidaNaoDerrubaAImportacao() {
        LinhaColetaCsvDto linha = new LinhaColetaCsvDto(
                "SAFRA_2026", "SCP-16005_P1_Pl1", "SCP-16005", "1", 1,
                "10", 10, true, "27/10 10:00", -14.789, -39.278
        );
        Planta planta = new Planta(parcela, "SCP-16005_P1_Pl1", 1, 1, SituacaoPlanta.NORMAL);
        byte[] lixo = "isto nao e imagem".getBytes(StandardCharsets.UTF_8);

        when(csvColetaParser.parse(any(InputStream.class))).thenReturn(List.of(linha));
        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(leituraColetaRepository.findByChaveIdempotencia(anyString())).thenReturn(Optional.empty());
        when(plantaRepository.findByPlantCodeIgnoreCase("SCP-16005_P1_Pl1")).thenReturn(Optional.of(planta));
        when(fotoStorageService.salvar(lixo)).thenThrow(new RegraNegocioException("Formato de imagem nao suportado."));

        ResultadoImportacaoCsvResponse resp = coletaSyncService.importarCsv(
                "TC-1801",
                new ByteArrayInputStream("mock".getBytes(StandardCharsets.UTF_8)),
                "Coleta.csv",
                "pesquisador@cepec.gov.br",
                List.of(new ArquivoFoto("SCP-16005_P1_Pl1_1761693000123.jpg", lixo))
        );

        assertThat(resp.leiturasNovasInseridas()).isEqualTo(1);
        assertThat(resp.fotosAnexadas()).isZero();
        assertThat(resp.fotosIgnoradas()).isEqualTo(1);
        assertThat(resp.avisos()).anyMatch(a -> a.contains("rejeitada"));
    }

    @Test
    @DisplayName("Sync em JSON: deve gravar as caixas do YOLO e tratar 'Não Coletável' como 0 frutos")
    void sincronizarJsonGravaCaixasDoYolo() {
        Planta planta = new Planta(parcela, "SCP-16005_P1_Pl1", 1, 1, SituacaoPlanta.NORMAL);
        Planta plantaMorta = new Planta(parcela, "SCP-16005_P1_Pl2", 2, 2, SituacaoPlanta.NORMAL);

        var item = new SyncJsonRequest.ItemSync(
                "SAFRA_2026", "SCP-16005_P1_Pl1", "SCP-16005", "1", 1, "2", "28/10 09:00", -14.78, -39.27,
                List.of(new DeteccaoDto("fruto_maduro", 0.91, 0.10, 0.20, 0.15, 0.25),
                        new DeteccaoDto("monilia", 0.77, 0.50, 0.40, 0.12, 0.18))
        );
        var morta = new SyncJsonRequest.ItemSync(
                "SAFRA_2026", "SCP-16005_P1_Pl2", "SCP-16005", "1", 2, "Não Coletável", "28/10 09:05", null, null, null
        );

        when(ensaioRepository.findByCodigoIgnoreCase("TC-1801")).thenReturn(Optional.of(ensaio));
        when(leituraColetaRepository.findByChaveIdempotencia(anyString())).thenReturn(Optional.empty());
        when(plantaRepository.findByPlantCodeIgnoreCase("SCP-16005_P1_Pl1")).thenReturn(Optional.of(planta));
        when(plantaRepository.findByPlantCodeIgnoreCase("SCP-16005_P1_Pl2")).thenReturn(Optional.of(plantaMorta));

        ResultadoImportacaoCsvResponse resp = coletaSyncService.sincronizarJson(
                new SyncJsonRequest("TC-1801", List.of(item, morta)), "pesquisador@cepec.gov.br");

        assertThat(resp.leiturasNovasInseridas()).isEqualTo(2);
        assertThat(resp.totalFrutosImportadosLote()).isEqualTo(2);
        assertThat(resp.plantasMarcadasNaoColetaveis()).isEqualTo(1);

        ArgumentCaptor<LeituraColeta> captor = ArgumentCaptor.forClass(LeituraColeta.class);
        verify(leituraColetaRepository, times(2)).save(captor.capture());
        LeituraColeta comCaixas = captor.getAllValues().get(0);
        assertThat(comCaixas.getDeteccoesJson()).contains("fruto_maduro").contains("monilia");
        assertThat(captor.getAllValues().get(1).getDeteccoesJson()).isNull();
        assertThat(captor.getAllValues().get(1).getQtdFrutos()).isZero();
    }
}
