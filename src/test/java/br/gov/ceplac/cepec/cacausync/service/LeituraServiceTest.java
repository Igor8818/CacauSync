package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.*;
import br.gov.ceplac.cepec.cacausync.dto.LeituraColetaResponse;
import br.gov.ceplac.cepec.cacausync.dto.ValidarLeituraRequest;
import br.gov.ceplac.cepec.cacausync.exception.RecursoNaoEncontradoException;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.repository.LeituraColetaRepository;
import br.gov.ceplac.cepec.cacausync.repository.PlantaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeituraServiceTest {

    @Mock
    private LeituraColetaRepository leituraRepository;
    @Mock
    private PlantaRepository plantaRepository;
    @Mock
    private FotoStorageService fotoStorageService;
    @Spy
    private LeituraMapper leituraMapper = new LeituraMapper(new ObjectMapper());

    @InjectMocks
    private LeituraService leituraService;

    private Planta planta;

    @BeforeEach
    void montarCenario() {
        Ensaio ensaio = new Ensaio("TC-1801", "Ensaio", "CEPEC");
        Clone clone = new Clone("CCN-51", "Clone", "CEPEC");
        planta = new Planta(new Parcela(ensaio, clone, 1, "20"), "CCN-51_P20_Pl1", 1, 1, SituacaoPlanta.NORMAL);
    }

    private LeituraColeta leitura(int frutos, boolean coletavel) {
        LeituraColeta l = new LeituraColeta();
        l.setId(7L);
        l.setPlanta(planta);
        l.setQtdFrutos(frutos);
        l.setColetavel(coletavel);
        l.setDiagnosticoBruto(coletavel ? String.valueOf(frutos) : "Não Coletável");
        return l;
    }

    private void leituraExiste(LeituraColeta l) {
        when(leituraRepository.findById(7L)).thenReturn(Optional.of(l));
        lenient().when(leituraRepository.save(any(LeituraColeta.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("VALIDADA: concorda com a IA — vale a contagem original e registra quem validou")
    void validarMantemContagemDaIa() {
        leituraExiste(leitura(12, true));

        LeituraColetaResponse r = leituraService.validar(7L,
                new ValidarLeituraRequest(StatusValidacao.VALIDADA, null, "  foto nítida  "), "pesq@cepec.gov.br");

        assertThat(r.statusValidacao()).isEqualTo(StatusValidacao.VALIDADA);
        assertThat(r.qtdEfetiva()).isEqualTo(12);
        assertThat(r.validadoPor()).isEqualTo("pesq@cepec.gov.br");
        assertThat(r.validadoEm()).isNotNull();
        assertThat(r.observacaoValidacao()).isEqualTo("foto nítida");
    }

    @Test
    @DisplayName("AJUSTADA: vale o número do pesquisador, e exige que ele informe a contagem")
    void ajustarUsaContagemDoPesquisador() {
        leituraExiste(leitura(12, true));

        assertThatThrownBy(() -> leituraService.validar(7L,
                new ValidarLeituraRequest(StatusValidacao.AJUSTADA, null, null), "p@x"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("qtdFrutosValidada");

        LeituraColetaResponse r = leituraService.validar(7L,
                new ValidarLeituraRequest(StatusValidacao.AJUSTADA, 15, null), "p@x");

        assertThat(r.qtdFrutos()).isEqualTo(12);
        assertThat(r.qtdFrutosValidada()).isEqualTo(15);
        assertThat(r.qtdEfetiva()).isEqualTo(15);
    }

    @Test
    @DisplayName("Ajustar para >0 uma leitura 'Não Coletável' reativa a planta: o pesquisador viu frutos na foto")
    void ajustarNaoColetavelParaMaisQueZeroReativaAPlanta() {
        planta.setSituacao(SituacaoPlanta.NAO_COLETAVEL);
        LeituraColeta l = leitura(0, false);
        leituraExiste(l);

        leituraService.validar(7L, new ValidarLeituraRequest(StatusValidacao.AJUSTADA, 4, "tinha frutos atrás da folha"), "p@x");

        assertThat(planta.getSituacao()).isEqualTo(SituacaoPlanta.NORMAL);
        assertThat(l.getColetavel()).isTrue();
        assertThat(l.contaNoRelatorio()).isTrue();
        verify(plantaRepository).save(planta);
    }

    @Test
    @DisplayName("REJEITADA: a leitura sai do relatório (efetiva = 0)")
    void rejeitarTiraDoRelatorio() {
        LeituraColeta l = leitura(30, true);
        leituraExiste(l);

        LeituraColetaResponse r = leituraService.validar(7L,
                new ValidarLeituraRequest(StatusValidacao.REJEITADA, null, "foto tremida"), "p@x");

        assertThat(r.qtdEfetiva()).isZero();
        assertThat(l.contaNoRelatorio()).isFalse();
    }

    @Test
    @DisplayName("PENDENTE desfaz a validação e limpa responsável, data e contagem ajustada")
    void voltarParaPendenteLimpaTudo() {
        LeituraColeta l = leitura(10, true);
        leituraExiste(l);
        leituraService.validar(7L, new ValidarLeituraRequest(StatusValidacao.AJUSTADA, 8, "x"), "p@x");

        leituraService.validar(7L, new ValidarLeituraRequest(StatusValidacao.PENDENTE, null, null), "p@x");

        assertThat(l.getStatusValidacao()).isEqualTo(StatusValidacao.PENDENTE);
        assertThat(l.getQtdFrutosValidada()).isNull();
        assertThat(l.getValidadoPor()).isNull();
        assertThat(l.getValidadoEm()).isNull();
        assertThat(l.qtdEfetiva()).isEqualTo(10);
    }

    @Test
    @DisplayName("Substituir a foto grava a nova e apaga a antiga do disco")
    void substituirFotoApagaAAntiga() {
        LeituraColeta l = leitura(5, true);
        l.setFotoChave("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg");
        leituraExiste(l);
        byte[] novo = {1, 2, 3};
        when(fotoStorageService.salvar(novo))
                .thenReturn(new FotoStorageService.FotoSalva("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg", "image/jpeg"));

        LeituraColetaResponse r = leituraService.anexarFoto(7L, novo);

        assertThat(r.temFoto()).isTrue();
        assertThat(l.getFotoChave()).isEqualTo("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg");
        verify(fotoStorageService).remover("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg");
    }

    @Test
    @DisplayName("Leitura inexistente ou sem foto devolve 'não encontrado'")
    void naoEncontrado() {
        when(leituraRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> leituraService.buscar(99L)).isInstanceOf(RecursoNaoEncontradoException.class);

        leituraExiste(leitura(1, true));
        assertThatThrownBy(() -> leituraService.carregarFoto(7L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("foto");
    }
}
