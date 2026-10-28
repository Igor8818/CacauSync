package br.gov.ceplac.cepec.cacausync.controller;

import br.gov.ceplac.cepec.cacausync.dto.CriarEnsaioRequest;
import br.gov.ceplac.cepec.cacausync.dto.EnsaioResumoResponse;
import br.gov.ceplac.cepec.cacausync.dto.LeituraColetaResponse;
import br.gov.ceplac.cepec.cacausync.dto.RelatorioProdutividadeEnsaioResponse;
import br.gov.ceplac.cepec.cacausync.service.ColetaSyncService;
import br.gov.ceplac.cepec.cacausync.service.EnsaioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ensaios")
@Tag(name = "3. Ensaios Genéticos, Rotas e Relatórios", description = "Planejamento de rotas para o FieldBook Android e relatórios de produtividade por clone")
public class EnsaioController {

    private final EnsaioService ensaioService;
    private final ColetaSyncService coletaSyncService;

    public EnsaioController(EnsaioService ensaioService, ColetaSyncService coletaSyncService) {
        this.ensaioService = ensaioService;
        this.coletaSyncService = coletaSyncService;
    }

    @GetMapping
    @Operation(summary = "Listar todos os ensaios genéticos cadastrados no CEPEC")
    public ResponseEntity<List<EnsaioResumoResponse>> listarEnsaios() {
        return ResponseEntity.ok(ensaioService.listarEnsaios());
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cadastrar novo ensaio genético (Exige ROLE_COORDENADOR)")
    public ResponseEntity<EnsaioResumoResponse> criarEnsaio(@Valid @RequestBody CriarEnsaioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ensaioService.criarEnsaio(request));
    }

    @GetMapping("/{codigo}/relatorio-clones")
    @Operation(
            summary = "Gerar relatório consolidado de produtividade e sobrevivência por clone",
            description = "Calcula total de frutos, média de frutos por planta viva (descontando plantas Não Coletáveis) e taxa de sobrevivência."
    )
    public ResponseEntity<RelatorioProdutividadeEnsaioResponse> relatorioProdutividade(
            @PathVariable String codigo
    ) {
        return ResponseEntity.ok(ensaioService.gerarRelatorioProdutividade(codigo));
    }

    @GetMapping("/{codigo}/leituras")
    @Operation(summary = "Listar leituras de coleta do ensaio de forma paginada com auditoria de GPS")
    public ResponseEntity<Page<LeituraColetaResponse>> listarLeituras(
            @PathVariable String codigo,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(coletaSyncService.listarLeiturasPorEnsaio(codigo, pageable));
    }

    @GetMapping(value = "/{codigo}/rota-csv", produces = "text/csv")
    @Operation(
            summary = "Baixar planilha CSV de Rota Pré-Campo para o Modo Rota (FieldBook) do CacauApp",
            description = "Exporta o CSV no formato exato de dados_cacau.csv para carregamento offline no Android antes de ir a campo."
    )
    public ResponseEntity<byte[]> exportarRotaCsv(@PathVariable String codigo) {
        String csv = ensaioService.exportarRotaCsvParaFieldBook(codigo);
        String filename = "dados_cacau_" + codigo.toLowerCase() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
