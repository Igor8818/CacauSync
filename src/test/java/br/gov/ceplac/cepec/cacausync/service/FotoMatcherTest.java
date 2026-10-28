package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.dto.ArquivoFoto;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FotoMatcherTest {

    private final FotoMatcher matcher = new FotoMatcher();

    private LinhaColetaCsvDto linha(String plantId) {
        return new LinhaColetaCsvDto("SAFRA", plantId, "CCN-51", "1", 1, "10", 10, true, "27/10 10:00", -14.0, -39.0);
    }

    private ArquivoFoto foto(String nome) {
        return new ArquivoFoto(nome, new byte[]{1});
    }

    @Test
    @DisplayName("Casa a foto com a linha pelo padrão do app: <ID_Gerado>_<milissegundos>.jpg (ignorando maiúsculas e extensão)")
    void casaPeloNomeDoArquivo() {
        var linhas = List.of(linha("CCN-51_Parcela1_Planta3"), linha("CCN-51_Parcela1_Planta4"));
        var fotos = List.of(foto("ccn-51_parcela1_planta4_1761693000123.JPG"));

        var r = matcher.casar(linhas, fotos);

        assertThat(r.fotoPorLinha().get(0)).isNull();
        assertThat(r.fotoPorLinha().get(1)).isSameAs(fotos.get(0));
        assertThat(r.fotosSemPar()).isZero();
    }

    @Test
    @DisplayName("REFAZER FOTO: quando a planta tem duas fotos e uma linha, vale a mais recente")
    void usaAFotoMaisRecenteQuandoRefeita() {
        var linhas = List.of(linha("SCP-16005_P1_Pl1"));
        var antiga = foto("SCP-16005_P1_Pl1_1761693000000.jpg");
        var nova = foto("SCP-16005_P1_Pl1_1761693999999.jpg");

        var r = matcher.casar(linhas, List.of(nova, antiga));

        assertThat(r.fotoPorLinha().get(0)).isSameAs(nova);
        assertThat(r.fotosSemPar()).isEqualTo(1);
    }

    @Test
    @DisplayName("Duas linhas da mesma planta e duas fotos: alinha em ordem cronológica")
    void alinhaLinhasRepetidasEmOrdem() {
        var linhas = List.of(linha("CCN-51_Parcela1_Planta1"), linha("CCN-51_Parcela1_Planta1"));
        var primeira = foto("CCN-51_Parcela1_Planta1_1761690000000.jpg");
        var segunda = foto("CCN-51_Parcela1_Planta1_1761690500000.jpg");

        var r = matcher.casar(linhas, List.of(segunda, primeira));

        assertThat(r.fotoPorLinha().get(0)).isSameAs(primeira);
        assertThat(r.fotoPorLinha().get(1)).isSameAs(segunda);
    }

    @Test
    @DisplayName("Foto sem carimbo de tempo, de outra planta, ou com sufixo '(1)' do Android é tratada corretamente")
    void tratanomesForaDoPadraoESufixoDuplicado() {
        var linhas = List.of(linha("CCN-51_Parcela1_Planta1"));
        var comSufixo = foto("CCN-51_Parcela1_Planta1_1761690000000 (1).jpg");
        var semCarimbo = foto("IMG_20261028.jpg");
        var outra = foto("PS-13.19_P2_Pl9_1761690000000.jpg");

        var r = matcher.casar(linhas, List.of(semCarimbo, outra, comSufixo));

        assertThat(r.fotoPorLinha().get(0)).isSameAs(comSufixo);
        assertThat(r.fotosSemPar()).isEqualTo(2);
    }

    @Test
    @DisplayName("Sem fotos: nenhuma linha recebe foto e nada fica sem par")
    void semFotos() {
        var r = matcher.casar(List.of(linha("A_P1_Pl1")), List.of());

        assertThat(r.fotoPorLinha()).containsExactly((ArquivoFoto) null);
        assertThat(r.fotosSemPar()).isZero();
    }
}
