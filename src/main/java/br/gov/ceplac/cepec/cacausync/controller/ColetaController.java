package br.gov.ceplac.cepec.cacausync.controller;

import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoImportacaoCsvResponse;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.service.ColetaSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/coletas")
@Tag(name = "2. Sincronização de Campo (CSV)", description = "Ingestão e auditoria de planilhas CSV exportadas pelo CacauApp Android")
@SecurityRequirement(name = "bearerAuth")
public class ColetaController {

    private final ColetaSyncService coletaSyncService;

    public ColetaController(ColetaSyncService coletaSyncService) {
        this.coletaSyncService = coletaSyncService;
    }

    @PostMapping(value = "/importar-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Importar planilha CSV de coleta exportada pelo CacauApp Android",
            description = "Lê o formato exportarCSVCEPEC_Personalizado (9 colunas) ou exportarCSVFinal (7 colunas), aplica idempotência, atualiza mortalidade ('Não Coletável') e audita coordenadas GPS."
    )
    public ResponseEntity<ResultadoImportacaoCsvResponse> importarCsv(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "codigoEnsaio", defaultValue = "TC-1801") String codigoEnsaio,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraNegocioException("Selecione um arquivo CSV válido exportado pelo aplicativo Android.");
        }

        String emailPesquisador = usuarioLogado != null ? usuarioLogado.getEmail() : "pesquisador@cepec.gov.br";
        try {
            ResultadoImportacaoCsvResponse resposta = coletaSyncService.importarCsv(
                    codigoEnsaio,
                    arquivo.getInputStream(),
                    arquivo.getOriginalFilename(),
                    emailPesquisador
            );
            return ResponseEntity.ok(resposta);
        } catch (IOException e) {
            throw new RegraNegocioException("Erro ao abrir stream do arquivo CSV enviado: " + e.getMessage());
        }
    }
}
