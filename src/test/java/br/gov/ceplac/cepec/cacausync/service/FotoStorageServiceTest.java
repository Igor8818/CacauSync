package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FotoStorageServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @TempDir
    Path pasta;

    @Test
    @DisplayName("Salva JPEG com nome aleatório (nunca o nome do cliente) e consegue ler de volta")
    void salvaECarregaJpeg() {
        var service = new FotoStorageService(pasta.toString());

        var salva = service.salvar(JPEG);

        assertThat(salva.chave()).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(salva.contentType()).isEqualTo("image/jpeg");
        assertThat(service.carregar(salva.chave())).isEqualTo(JPEG);
    }

    @Test
    @DisplayName("Reconhece PNG pelos bytes iniciais")
    void salvaPng() {
        var salva = new FotoStorageService(pasta.toString()).salvar(PNG);

        assertThat(salva.chave()).endsWith(".png");
        assertThat(salva.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Não confia na extensão/Content-Type: arquivo que não é imagem é recusado")
    void recusaArquivoQueNaoEImagem() {
        var service = new FotoStorageService(pasta.toString());

        assertThatThrownBy(() -> service.salvar("<script>alert(1)</script>".getBytes()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("JPEG ou PNG");
        assertThatThrownBy(() -> service.salvar(new byte[0]))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    @DisplayName("Bloqueia path traversal: chave fora do formato UUID não sai da pasta de fotos")
    void bloqueiaPathTraversal() {
        var service = new FotoStorageService(pasta.toString());

        assertThatThrownBy(() -> service.carregar("../../etc/passwd"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("invalida");
        assertThatThrownBy(() -> service.carregar("..\\..\\Windows\\win.ini"))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Remover apaga o arquivo do disco")
    void removeArquivo() {
        var service = new FotoStorageService(pasta.toString());
        var salva = service.salvar(JPEG);

        service.remover(salva.chave());

        assertThatThrownBy(() -> service.carregar(salva.chave()))
                .hasMessageContaining("nao encontrado");
    }
}
