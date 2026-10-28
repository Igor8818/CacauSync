package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.exception.RecursoNaoEncontradoException;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Guarda as fotos no disco do servidor (pasta configuravel) e devolve uma "chave" aleatoria.
 * O banco guarda so a chave; o nome original do arquivo nunca vira caminho no disco.
 * Isso evita "path traversal" (alguem mandar um arquivo chamado ../../algo para sobrescrever coisas).
 */
@Service
public class FotoStorageService {

    private static final Pattern CHAVE_VALIDA = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$");
    static final long TAMANHO_MAXIMO_BYTES = 15L * 1024 * 1024;

    private final Path pastaBase;

    public FotoStorageService(@Value("${app.storage.fotos-dir:./storage/fotos}") String pasta) {
        this.pastaBase = Paths.get(pasta).toAbsolutePath().normalize();
    }

    public record FotoSalva(String chave, String contentType) {}

    public FotoSalva salvar(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraNegocioException("A foto enviada esta vazia.");
        }
        if (conteudo.length > TAMANHO_MAXIMO_BYTES) {
            throw new RegraNegocioException("A foto excede o limite de 15 MB.");
        }
        String extensao;
        String contentType;
        if (ehJpeg(conteudo)) {
            extensao = "jpg";
            contentType = "image/jpeg";
        } else if (ehPng(conteudo)) {
            extensao = "png";
            contentType = "image/png";
        } else {
            // Nao confiamos no Content-Type informado pelo cliente: olhamos os bytes iniciais do arquivo.
            throw new RegraNegocioException("Formato de imagem nao suportado. Envie JPEG ou PNG.");
        }

        String chave = UUID.randomUUID() + "." + extensao;
        try {
            Files.createDirectories(pastaBase);
            Files.write(resolver(chave), conteudo);
        } catch (IOException e) {
            throw new RegraNegocioException("Nao foi possivel gravar a foto no servidor: " + e.getMessage());
        }
        return new FotoSalva(chave, contentType);
    }

    public byte[] carregar(String chave) {
        Path arquivo = resolver(chave);
        try {
            if (!Files.exists(arquivo)) {
                throw new RecursoNaoEncontradoException("Arquivo de foto nao encontrado no servidor.");
            }
            return Files.readAllBytes(arquivo);
        } catch (IOException e) {
            throw new RegraNegocioException("Nao foi possivel ler a foto: " + e.getMessage());
        }
    }

    public void remover(String chave) {
        if (chave == null || chave.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolver(chave));
        } catch (IOException ignorado) {
            // Foto orfa no disco nao quebra o fluxo; sera ignorada.
        }
    }

    private Path resolver(String chave) {
        if (chave == null || !CHAVE_VALIDA.matcher(chave).matches()) {
            throw new RegraNegocioException("Chave de foto invalida.");
        }
        Path resolvido = pastaBase.resolve(chave).normalize();
        if (!resolvido.startsWith(pastaBase)) {
            throw new RegraNegocioException("Chave de foto invalida.");
        }
        return resolvido;
    }

    private boolean ehJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private boolean ehPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }
}
