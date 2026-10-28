package br.gov.ceplac.cepec.cacausync.controller;

import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import br.gov.ceplac.cepec.cacausync.dto.LeituraColetaResponse;
import br.gov.ceplac.cepec.cacausync.dto.ValidarLeituraRequest;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.service.LeituraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/leituras")
@Tag(name = "4. Auditoria e Validação", description = "O pesquisador confere a foto e a contagem da IA e valida, ajusta ou rejeita")
@SecurityRequirement(name = "bearerAuth")
public class LeituraController {

    private final LeituraService leituraService;

    public LeituraController(LeituraService leituraService) {
        this.leituraService = leituraService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe de uma leitura (inclui caixas do YOLO e status de validação)")
    public ResponseEntity<LeituraColetaResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(leituraService.buscar(id));
    }

    @GetMapping("/{id}/foto")
    @Operation(summary = "Baixar a foto da leitura (exige token: o painel busca via fetch com Authorization)")
    public ResponseEntity<byte[]> foto(@PathVariable Long id) {
        LeituraService.FotoCarregada foto = leituraService.carregarFoto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(foto.conteudo());
    }

    @PostMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Anexar ou substituir a foto de uma leitura (JPEG ou PNG, até 15 MB)")
    public ResponseEntity<LeituraColetaResponse> anexarFoto(
            @PathVariable Long id,
            @RequestParam("foto") MultipartFile foto
    ) {
        if (foto == null || foto.isEmpty()) {
            throw new RegraNegocioException("Selecione uma foto.");
        }
        try {
            return ResponseEntity.ok(leituraService.anexarFoto(id, foto.getBytes()));
        } catch (IOException e) {
            throw new RegraNegocioException("Erro ao ler a foto enviada: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/validacao")
    @Operation(
            summary = "Validar, ajustar ou rejeitar a contagem da IA",
            description = "VALIDADA = concordo; AJUSTADA = vale a contagem que eu informar; REJEITADA = fora do relatório; "
                    + "PENDENTE = desfazer. Registra quem validou e quando."
    )
    public ResponseEntity<LeituraColetaResponse> validar(
            @PathVariable Long id,
            @Valid @RequestBody ValidarLeituraRequest request,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(leituraService.validar(id, request, usuarioLogado.getEmail()));
    }
}
