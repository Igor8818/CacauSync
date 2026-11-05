package br.gov.ceplac.cepec.cacausync.controller;

import br.gov.ceplac.cepec.cacausync.domain.entity.StatusValidacao;
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
import org.springframework.data.domain.Sort;
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
@SecurityRequirement(name = "bearerAuth")
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
            description = "Cada planta conta uma vez (leitura válida mais recente). Média por planta viva avaliada, cobertura da coleta, "
                    + "frutos sadios x doentes e estimativa indicativa em kg/ha e @/ha (SPEC-003). "
                    + "Parâmetros opcionais: indiceFruto (frutos sadios por kg de amêndoa seca, padrão 22), "
                    + "plantasPorHa (padrão 1111) e campanha (filtra as leituras)."
    )
    public ResponseEntity<RelatorioProdutividadeEnsaioResponse> relatorioProdutividade(
            @PathVariable String codigo,
            @RequestParam(value = "indiceFruto", required = false) Double indiceFruto,
            @RequestParam(value = "plantasPorHa", required = false) Integer plantasPorHa,
            @RequestParam(value = "campanha", required = false) String campanha
    ) {
        return ResponseEntity.ok(ensaioService.gerarRelatorioProdutividade(codigo, indiceFruto, plantasPorHa, campanha));
    }

    @GetMapping("/{codigo}/leituras")
    @Operation(
            summary = "Listar leituras de coleta do ensaio (paginado) para auditoria",
            description = "Use ?status=PENDENTE para ver só o que ainda falta o pesquisador conferir."
    )
    public ResponseEntity<Page<LeituraColetaResponse>> listarLeituras(
            @PathVariable String codigo,
            @RequestParam(value = "status", required = false) StatusValidacao status,
            @PageableDefault(size = 20, sort = "importadoEm", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(coletaSyncService.listarLeiturasPorEnsaio(codigo, status, pageable));
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

    @GetMapping(value = "/{codigo}/analise-csv", produces = "text/csv")
    @Operation(
            summary = "Baixar planilha analítica consolidada (pós-auditoria) com doenças e validações",
            description = "Exporta todas as leituras do ensaio com contagens da IA, contagens validadas, Sadios, Vassoura, Podridão, Não Avaliável, GPS e auditor."
    )
    public ResponseEntity<byte[]> exportarAnaliseCsv(@PathVariable String codigo) {
        String csv = ensaioService.exportarAnaliseCompletaCsv(codigo);
        String filename = "analise_auditada_" + codigo.toLowerCase() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
