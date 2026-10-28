package br.gov.ceplac.cepec.cacausync.controller;

import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import br.gov.ceplac.cepec.cacausync.dto.ArquivoFoto;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoImportacaoCsvResponse;
import br.gov.ceplac.cepec.cacausync.dto.SyncJsonRequest;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.service.ColetaSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/coletas")
@Tag(name = "2. Sincronização de Campo", description = "Ingestão das coletas do CacauApp: CSV + fotos (navegador do celular) ou JSON (futuro botão do app)")
@SecurityRequirement(name = "bearerAuth")
public class ColetaController {

    private final ColetaSyncService coletaSyncService;

    public ColetaController(ColetaSyncService coletaSyncService) {
        this.coletaSyncService = coletaSyncService;
    }

    @PostMapping(value = "/importar-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Importar CSV do CacauApp, opcionalmente com as fotos da coleta",
            description = "Lê o formato exportarCSVCEPEC_Personalizado (9 colunas) ou exportarCSVFinal (7 colunas). "
                    + "As fotos (campo 'fotos', várias) são casadas com as linhas pelo nome do arquivo "
                    + "'<ID_Gerado>_<milissegundos>.jpg', que é como o app salva em Pictures/CEPEC_Analise. "
                    + "Reenviar o mesmo CSV não duplica: só anexa fotos que ainda faltavam."
    )
    public ResponseEntity<ResultadoImportacaoCsvResponse> importarCsv(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "fotos", required = false) List<MultipartFile> fotos,
            @RequestParam(value = "codigoEnsaio", defaultValue = "TC-1801") String codigoEnsaio,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraNegocioException("Selecione um arquivo CSV válido exportado pelo aplicativo Android.");
        }
        try {
            ResultadoImportacaoCsvResponse resposta = coletaSyncService.importarCsv(
                    codigoEnsaio,
                    arquivo.getInputStream(),
                    arquivo.getOriginalFilename(),
                    usuarioLogado.getEmail(),
                    converterFotos(fotos)
            );
            return ResponseEntity.ok(resposta);
        } catch (IOException e) {
            throw new RegraNegocioException("Erro ao ler os arquivos enviados: " + e.getMessage());
        }
    }

    @PostMapping(value = "/sync-json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Sincronizar leituras em JSON (inclui as caixas do YOLO)",
            description = "Contrato para o app enviar direto, sem CSV. Cada leitura pode trazer 'deteccoes' "
                    + "(classe, confiança e caixa normalizada 0–1) para o pesquisador conferir sobre a foto. "
                    + "As fotos sobem depois em POST /api/v1/leituras/{id}/foto."
    )
    public ResponseEntity<ResultadoImportacaoCsvResponse> syncJson(
            @Valid @RequestBody SyncJsonRequest request,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(coletaSyncService.sincronizarJson(request, usuarioLogado.getEmail()));
    }

    private List<ArquivoFoto> converterFotos(List<MultipartFile> fotos) throws IOException {
        List<ArquivoFoto> resultado = new ArrayList<>();
        if (fotos == null) {
            return resultado;
        }
        for (MultipartFile f : fotos) {
            if (f != null && !f.isEmpty()) {
                resultado.add(new ArquivoFoto(f.getOriginalFilename(), f.getBytes()));
            }
        }
        return resultado;
    }
}
